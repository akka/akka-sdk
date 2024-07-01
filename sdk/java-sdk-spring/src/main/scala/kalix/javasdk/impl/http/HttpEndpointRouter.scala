/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.http

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.control.NonFatal

import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods
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
import kalix.javasdk.impl.http.PathTree.JavaMethodInvoker
import kalix.javasdk.impl.http.PathTree.OpenPathTree
import kalix.javasdk.impl.http.PathTree.ParameterizedMethodInvoker
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
object HttpEndpointRouter {

  private val CorrelationIdMdcKey = "correlationID"
}

/**
 * INTERNAL API
 */
@InternalApi
case class HttpEndpointRouter(
    getTree: PathTree,
    postTree: PathTree,
    putTree: PathTree,
    patchTree: PathTree,
    deleteTree: PathTree) {

  def invokerFor(method: HttpMethod, path: Path): Option[ParameterizedMethodInvoker] = {
    method match {
      case HttpMethods.GET    => getTree.invokerFor(path)
      case HttpMethods.POST   => postTree.invokerFor(path)
      case HttpMethods.PUT    => putTree.invokerFor(path)
      case HttpMethods.PATCH  => patchTree.invokerFor(path)
      case HttpMethods.DELETE => deleteTree.invokerFor(path)
      case _                  => throw new RuntimeException(s"Unsupported http method: $method")
    }
  }

  // this extractor allows us to combine the HttpRequest with a ParameterizedMethodInvoker
  private object HttpRequestExtractor {
    def unapply(request: HttpRequest): Option[(HttpRequest, ParameterizedMethodInvoker)] = {

      val method = request.method
      val path = request.uri.path

      invokerFor(method, path)
        .map { invoker => (request, invoker) }
    }
  }

  def route(timeout: FiniteDuration)(implicit
      sys: ClassicActorSystemProvider): PartialFunction[HttpRequest, Future[HttpResponse]] = {

    case HttpRequestExtractor(req, methodInvoker) =>
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
            handleResponse(methodInvoker.invoke(deserBody))
          }
        case None =>
          // FIXME we must always consume, but should we also fail if there is a request payload for a no-body method?
          req.entity.discardBytes().future().flatMap(_ => handleResponse(methodInvoker.invoke()))
      }).recover(defaultErrorHandling(methodInvoker.javaMethod))
  }

  private def defaultErrorHandling(targetMethod: Method): PartialFunction[Throwable, HttpResponse] = {

    def loggerForEndpoint = LoggerFactory.getLogger(targetMethod.getDeclaringClass)

    {
      case ex: InvocationTargetException if ex.getCause != null =>
        // Unfold invocation target exception so we get the actual user exception
        defaultErrorHandling(targetMethod)(ex.getCause)
      case ex: CompletionException if ex.getCause != null =>
        // Unfold nested Java CS exception so we get the actual user exception
        defaultErrorHandling(targetMethod)(ex.getCause)
      case ex: IllegalArgumentException =>
        if (ex.getMessage != null) {
          loggerForEndpoint.debug("Bad request: {}", ex.getMessage)
        }
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
        MDC.put(HttpEndpointRouter.CorrelationIdMdcKey, correlationId)
        // FIXME include what endpoint path it was
        loggerForEndpoint.warn(s"Endpoint error [correlation id $correlationId]", ex)
        MDC.remove(HttpEndpointRouter.CorrelationIdMdcKey)
        HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(s"Unexpected error [$correlationId]"))
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
object HttpEndpointOpenRouter {

  def apply(cls: Class[_], instanceFactory: () => Any): HttpEndpointOpenRouter = {

    val mainPath = Option(cls.getAnnotation(classOf[Endpoint])).map(_.value())

    def fullPath(methodPath: String) =
      mainPath.map(m => m + methodPath).getOrElse(methodPath)

    val getTree = PathTree.empty
    val postTree = PathTree.empty
    val putTree = PathTree.empty
    val patchTree = PathTree.empty
    val deleteTree = PathTree.empty

    // FIXME: enforce unique annotations per method, for now assuming only one
    cls.getDeclaredMethods.foreach { method =>
      if (method.getAnnotation(classOf[Get]) != null) {
        val path = method.getAnnotation(classOf[Get]).value()
        getTree.append(fullPath(path), JavaMethodInvoker(instanceFactory, method))

      } else if (method.getAnnotation(classOf[Post]) != null) {
        val path = method.getAnnotation(classOf[Post]).value()
        postTree.append(fullPath(path), JavaMethodInvoker(instanceFactory, method))

      } else if (method.getAnnotation(classOf[Put]) != null) {
        val path = method.getAnnotation(classOf[Put]).value()
        putTree.append(fullPath(path), JavaMethodInvoker(instanceFactory, method))

      } else if (method.getAnnotation(classOf[Patch]) != null) {
        val path = method.getAnnotation(classOf[Patch]).value()
        patchTree.append(fullPath(path), JavaMethodInvoker(instanceFactory, method))

      } else if (method.getAnnotation(classOf[Delete]) != null) {
        val path = method.getAnnotation(classOf[Delete]).value()
        deleteTree.append(fullPath(path), JavaMethodInvoker(instanceFactory, method))
      }
    }

    HttpEndpointOpenRouter(getTree, postTree, putTree, patchTree, deleteTree)
  }

  def empty: HttpEndpointOpenRouter =
    HttpEndpointOpenRouter(PathTree.empty, PathTree.empty, PathTree.empty, PathTree.empty, PathTree.empty)
}

/**
 * INTERNAL API
 */
@InternalApi
case class HttpEndpointOpenRouter(
    getTree: OpenPathTree,
    postTree: OpenPathTree,
    putTree: OpenPathTree,
    patchTree: OpenPathTree,
    deleteTree: OpenPathTree) {

  def ++(other: HttpEndpointOpenRouter): HttpEndpointOpenRouter = {
    HttpEndpointOpenRouter(
      getTree ++ other.getTree,
      postTree ++ other.postTree,
      putTree ++ other.putTree,
      patchTree ++ other.patchTree,
      deleteTree ++ other.deleteTree)
  }

  def seal: HttpEndpointRouter =
    HttpEndpointRouter(getTree.seal, postTree.seal, putTree.seal, patchTree.seal, deleteTree.seal)
}
