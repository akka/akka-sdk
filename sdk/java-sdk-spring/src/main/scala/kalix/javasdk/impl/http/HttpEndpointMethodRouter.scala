/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.http

import java.lang.reflect.Method
import java.util.concurrent.CompletionStage
import scala.annotation.tailrec
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.jdk.FutureConverters.CompletionStageOps
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods.{ DELETE => HttpDelete }
import akka.http.scaladsl.model.HttpMethods.{ GET => HttpGet }
import akka.http.scaladsl.model.HttpMethods.{ PATCH => HttpPatch }
import akka.http.scaladsl.model.HttpMethods.{ POST => HttpPost }
import akka.http.scaladsl.model.HttpMethods.{ PUT => HttpPut }
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path
import akka.stream.Materializer
import kalix.javasdk.JsonSupport
import kalix.javasdk.annotations.http.Delete
import kalix.javasdk.annotations.http.Endpoint
import kalix.javasdk.annotations.http.Get
import kalix.javasdk.annotations.http.Patch
import kalix.javasdk.annotations.http.Post
import kalix.javasdk.annotations.http.Put
import kalix.javasdk.impl.http.DynPathMatcher.MatchedResult
import kalix.javasdk.impl.http.HttpEndpointMethodRouter.HttpMethodInvoker
import org.slf4j.LoggerFactory
import org.slf4j.MDC

import java.lang.reflect.InvocationTargetException
import java.util.UUID
import java.util.concurrent.CompletionException
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
object HttpEndpointMethodRouter {

  // FIXME error logging with a logger for the component class?
  private val logger = LoggerFactory.getLogger(getClass)

  case class HttpMethodInvoker(
      instanceFactory: () => Any,
      httpMethod: HttpMethod,
      pathMatch: DynPathMatcher,
      javaMethod: Method) {

    val bodyType: Option[Class[_]] =
      Option.when(pathMatch.numberOfPathVariables + 1 == javaMethod.getParameterTypes.length) {
        javaMethod.getParameterTypes.drop(pathMatch.numberOfPathVariables)(0)
      }

    def invoke(methodParams: Array[Any]): Any =
      javaMethod.invoke(instanceFactory(), methodParams: _*)

    def invoke(methodParams: Array[Any], body: Any): Any =
      javaMethod.invoke(instanceFactory(), methodParams.appended(body): _*)

  }

  def apply(cls: Class[_], instanceFactory: () => Any): HttpEndpointMethodRouter = {

    val mainPath = Option(cls.getAnnotation(classOf[Endpoint])).map(_.value())

    def fullPath(methodPath: String) =
      mainPath.map(m => m + methodPath).getOrElse(methodPath)

    // FIXME: enforce unique annotations per method, for now assuming only one
    val methodInvokers =
      cls.getDeclaredMethods
        .collect {
          case method if method.getAnnotation(classOf[Get]) != null =>
            (HttpGet, method.getAnnotation(classOf[Get]).value(), method)

          case method if method.getAnnotation(classOf[Post]) != null =>
            (HttpPost, method.getAnnotation(classOf[Post]).value(), method)

          case method if method.getAnnotation(classOf[Put]) != null =>
            (HttpPut, method.getAnnotation(classOf[Put]).value(), method)

          case method if method.getAnnotation(classOf[Patch]) != null =>
            (HttpPatch, method.getAnnotation(classOf[Patch]).value(), method)

          case method if method.getAnnotation(classOf[Delete]) != null =>
            (HttpDelete, method.getAnnotation(classOf[Delete]).value(), method)

        }
        .map { case (httpMethod, path, method) =>
          val paramTypes = method.getParameterTypes
          HttpMethodInvoker(instanceFactory, httpMethod, DynPathMatcher.apply(fullPath(path), paramTypes), method)
        }

    HttpEndpointMethodRouter(methodInvokers.toIndexedSeq)
  }

  val empty: HttpEndpointMethodRouter = HttpEndpointMethodRouter(Seq.empty)

  private val CorrelationIdMdcKey = "correlationID"
  private val defaultErrorHandling: PartialFunction[Throwable, HttpResponse] = {
    case ex: InvocationTargetException if ex.getCause != null =>
      // Unfold invocation target exception so we get the actual user exception
      defaultErrorHandling(ex.getCause)
    case ex: CompletionException if ex.getCause != null =>
      // Unfold nested Java CS exception so we get the actual user exception
      defaultErrorHandling(ex.getCause)
    case ex: IllegalArgumentException =>
      if (ex.getMessage != null) logger.debug("Bad request: {}", ex.getMessage)
      HttpResponse(
        StatusCodes.BadRequest,
        entity = if (ex.getMessage != null) HttpEntity(ex.getMessage) else HttpEntity.Empty)
    case NonFatal(ex) =>
      // Note: the default is to not pass along potentially secret internal error details to clients but to
      // log in the service and pass a correlation id to the client that they can then hand to the
      // owner of the service for debugging
      // FIXME know that we are in test/dev mode and return the full error description in response, and only use correlation
      //       id in "prod" mode for faster local turnaround
      val correlationId = UUID.randomUUID().toString
      MDC.put(CorrelationIdMdcKey, correlationId)
      logger.warn(s"Endpoint error [correlation id $correlationId]", ex)
      MDC.remove(CorrelationIdMdcKey)
      HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(s"Unexpected error [$correlationId]"))
  }
}

/**
 * INTERNAL API
 */
@InternalApi
case class HttpEndpointMethodRouter(methodInvokers: Seq[HttpMethodInvoker]) {

  // sort methods from shortest to longest (in terms of node)
  // FIXME: build a tree structure and walk it like a pro
  private val methodsMap = methodInvokers.sortBy(_.pathMatch.annotationPath.toString()).groupBy(_.httpMethod)

  def findMethod(httpMethod: HttpMethod, path: Path): Option[(HttpMethodInvoker, Array[Any])] = {

    @tailrec
    def traverse(head: HttpMethodInvoker, tail: Seq[HttpMethodInvoker]): Option[(HttpMethodInvoker, Array[Any])] = {
      head.pathMatch(path) match {
        case MatchedResult(Path.Empty, _, extractedValues) => Some((head, extractedValues))
        case _ if tail.nonEmpty                            => traverse(tail.head, tail.tail)
        case _                                             => None
      }
    }

    methodsMap
      .get(httpMethod)
      .filter(_.nonEmpty)
      .flatMap(methods => traverse(methods.head, methods.tail))
  }

  def ++(other: HttpEndpointMethodRouter): HttpEndpointMethodRouter =
    HttpEndpointMethodRouter(this.methodInvokers ++ other.methodInvokers)

  // this extractor allows us to compose the HttpRequest with a matching MethodInvoker
  private object HttpRequestExtractor {
    def unapply(request: HttpRequest): Option[(HttpRequest, HttpMethodInvoker, Array[Any])] = {
      val method = request.method
      val path = request.uri.path
      findMethod(method, path).map { case (ink, params) => (request, ink, params) }
    }
  }

  def route(timeout: FiniteDuration)(implicit
      sys: ClassicActorSystemProvider): PartialFunction[HttpRequest, Future[HttpResponse]] = {

    case HttpRequestExtractor(req, methodInvoker, params) =>
      implicit val dispatcher: ExecutionContextExecutor = sys.classicSystem.dispatcher
      implicit val mat = Materializer.matFromSystem(sys)

      def toHttpResponse(res: Any): HttpResponse =
        res match {
          case null                  => HttpResponse(entity = HttpEntity.Empty)
          case httpRes: HttpResponse => httpRes
          case str: String           => HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, str))
          case res =>
            val bytes = JsonSupport.getObjectMapper.writerFor(res.getClass).writeValueAsBytes(res)
            HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, bytes))
        }

      // FIXME: what about headers? how to pass it to endpoint method, some request model?
      def handleResponse(response: Any): Future[HttpResponse] =
        response match {
          case resFut: CompletionStage[_] => resFut.asScala.map(toHttpResponse)
          case res                        => Future.successful(toHttpResponse(res))
        }

      (methodInvoker.bodyType match {
        case Some(bodyType) =>
          req.entity.toStrict(timeout).flatMap { body =>
            val deserBody =
              try {
                JsonSupport.parseBytes(body.data.toArrayUnsafe(), bodyType)
              } catch {
                case ex: com.fasterxml.jackson.core.JacksonException =>
                  // Jackson stacktrace does not contain anything very useful, but we know it was that it was
                  // not possible to parse the input
                  throw new IllegalArgumentException("Error parsing request json: " + ex.getMessage)
              }
            handleResponse(methodInvoker.invoke(params, deserBody))
          }
        case None =>
          // FIXME we must always consume, but should we also fail if there is a request payload for a no-body method?
          req.entity.discardBytes().future().flatMap(_ => handleResponse(methodInvoker.invoke(params)))
      }).recover(HttpEndpointMethodRouter.defaultErrorHandling)
  }

}
