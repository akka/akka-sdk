/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import java.io.IOException
import java.lang.{ Iterable => JIterable }
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.function.Function

import scala.concurrent.ExecutionException
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.jdk.DurationConverters.JavaDurationOps

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.javadsl.Http
import akka.http.javadsl.model.ContentType
import akka.http.javadsl.model.ContentTypes
import akka.http.javadsl.model.HttpCharset
import akka.http.javadsl.model.HttpEntity
import akka.http.javadsl.model.HttpHeader
import akka.http.javadsl.model.HttpMethod
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.model.headers.HttpCredentials
import akka.javasdk.JsonSupport
import akka.javasdk.http.HttpClient
import akka.javasdk.http.RequestBuilder
import akka.javasdk.http.StrictResponse
import akka.javasdk.impl.ErrorHandling
import akka.pattern.Patterns
import akka.pattern.RetrySettings
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonProcessingException

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class HttpClientImpl(
    http: Http,
    baseUrl: String,
    materializer: Materializer,
    timeout: FiniteDuration,
    defaultHeaders: Seq[HttpHeader],
    sdkExecutor: Executor)
    extends HttpClient {

  def this(system: ActorSystem[_], baseUrl: String, defaultHeaders: Seq[HttpHeader], sdkExecutor: Executor) =
    this(
      Http(system),
      baseUrl,
      SystemMaterializer.get(system).materializer,
      // 10s higher than configured timeout, so configured timeout always win
      system.settings.config.getDuration("akka.http.server.request-timeout").toScala + 10.seconds,
      defaultHeaders,
      sdkExecutor)

  def this(system: ActorSystem[_], baseUrl: String, sdkExecutor: Executor) =
    this(system, baseUrl, Seq.empty, sdkExecutor)

  override def GET(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.GET)

  override def POST(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.POST)

  override def PUT(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.PUT)

  override def PATCH(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.PATCH)

  override def DELETE(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.DELETE)

  private def forMethod(uri: String, method: HttpMethod): RequestBuilderImpl[ByteString] = {
    val req = HttpRequest.create(baseUrl + uri).withMethod(method)
    new RequestBuilderImpl[ByteString](
      http,
      materializer,
      timeout,
      req.withHeaders(defaultHeaders.asJava),
      new StrictResponse[ByteString](_, _),
      None,
      sdkExecutor)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final case class RequestBuilderImpl[R](
    http: Http,
    materializer: Materializer,
    timeout: FiniteDuration,
    request: HttpRequest,
    bodyParser: (HttpResponse, ByteString) => StrictResponse[R],
    retrySettings: Option[RetrySettings],
    sdkExecutor: Executor)
    extends RequestBuilder[R] {

  override def withRequest(request: HttpRequest): RequestBuilder[R] = copy(request = request)

  override def addHeader(header: String, value: String): RequestBuilder[R] = addHeader(HttpHeader.parse(header, value))

  override def addHeader(header: HttpHeader): RequestBuilder[R] = withRequest(request.addHeader(header))

  override def withHeaders(headers: JIterable[HttpHeader]): RequestBuilder[R] = withRequest(
    request.withHeaders(headers))

  override def addCredentials(credentials: HttpCredentials): RequestBuilder[R] = withRequest(
    request.addCredentials(credentials))

  override def withTimeout(timeout: Duration) =
    copy(timeout = timeout.toScala)

  override def modifyRequest(adapter: Function[HttpRequest, HttpRequest]): RequestBuilder[R] = withRequest(
    adapter.apply(request))

  override def withRequestBody(`object`: AnyRef): RequestBuilder[R] = {
    if (`object` eq null) throw new IllegalArgumentException("object must not be null")
    try {
      val body = JsonSupport.encodeToAkkaByteString(`object`)
      val requestWithBody = request.withEntity(ContentTypes.APPLICATION_JSON, body)
      withRequest(requestWithBody)
    } catch {
      case e: JsonProcessingException =>
        throw new RuntimeException(e)
    }
  }

  override def withRequestBody(text: String): RequestBuilder[R] = {
    if (text eq null) throw new IllegalArgumentException("text must not be null")
    val requestWithBody = request.withEntity(ContentTypes.TEXT_PLAIN_UTF8, text)
    withRequest(requestWithBody)
  }

  override def withRequestBody(bytes: Array[Byte]): RequestBuilder[R] = {
    val requestWithBody = request.withEntity(ContentTypes.APPLICATION_OCTET_STREAM, bytes)
    withRequest(requestWithBody)
  }

  override def withRequestBody(`type`: ContentType, bytes: Array[Byte]): RequestBuilder[R] = {
    val requestWithBody = request.withEntity(`type`, bytes)
    withRequest(requestWithBody)
  }

  override def invokeAsync: CompletionStage[StrictResponse[R]] = {

    def callHttp(): CompletionStage[StrictResponse[R]] = http
      .singleRequest(request)
      .thenComposeAsync(
        (response: HttpResponse) =>
          response.entity
            .toStrict(timeout.toMillis, materializer)
            .thenApplyAsync((entity: HttpEntity.Strict) => bodyParser.apply(response, entity.getData), sdkExecutor),
        sdkExecutor)

    retrySettings match {
      case Some(settings) => Patterns.retry(() => callHttp(), settings, materializer.system)
      case None           => callHttp()
    }
  }

  override def invoke(): StrictResponse[R] =
    try {
      invokeAsync.toCompletableFuture.get()
    } catch {
      case ex: ExecutionException => throw ErrorHandling.unwrapExecutionException(ex)
    }

  private[akka] def bodyParserInternal[T](
      classType: Class[T],
      res: HttpResponse,
      bytes: ByteString): StrictResponse[T] =
    try {
      if (res.status.isFailure) {
        onResponseError(res, bytes)
      } else if (!res.entity.getContentType.binary && (classType eq classOf[String])) {
        new StrictResponse[T](
          res,
          new String(
            bytes.toArrayUnsafe(),
            res.entity.getContentType.getCharsetOption
              .map[Charset]((c: HttpCharset) => c.nioCharset)
              .orElse(StandardCharsets.UTF_8)).asInstanceOf[T])
      } else if (res.entity.getContentType == ContentTypes.APPLICATION_JSON) {
        new StrictResponse[T](res, JsonSupport.decodeJson(classType, bytes))
      } else {
        throw new RuntimeException(
          "Expected to parse the response for " + request.getUri + " to " + classType + " but response content type is " +
          res.entity.getContentType)
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }

  override def responseBodyAs[T](classType: Class[T]): RequestBuilder[T] =
    withBodyParser((res: HttpResponse, bytes: ByteString) => bodyParserInternal(classType, res, bytes))

  override def responseBodyAsListOf[T](elementType: Class[T]): RequestBuilder[util.List[T]] =
    withBodyParser[util.List[T]] { (res: HttpResponse, bytes: ByteString) =>
      try if (res.status.isFailure) {
        onResponseError(res, bytes)
      } else if (res.entity.getContentType == ContentTypes.APPLICATION_JSON)
        new StrictResponse[util.List[T]](
          res,
          JsonSupport.getObjectMapper
            .readerForListOf(elementType)
            .readValue(bytes.toArrayUnsafe())
            .asInstanceOf[util.List[T]])
      else
        throw new RuntimeException(
          "Expected the response for " + request.getUri + " to be of type " + ContentTypes.APPLICATION_JSON + " but response content type is " + res.entity.getContentType)
      catch {
        case e: IOException =>
          throw new RuntimeException(e)
      }
    }

  private def onResponseError(response: HttpResponse, bytes: ByteString) = {
    // FIXME should we have a better way to deal with failure?
    // FIXME what about error responses with a body, now we can't expect/parse those
    val errorString = "HTTP request for [" + request.getUri + "] failed with HTTP status " + response.status
    if (response.entity.getContentType.binary) throw new RuntimeException(errorString)
    else {
      if (response.status.intValue() == StatusCodes.BAD_REQUEST.intValue())
        throw new IllegalArgumentException(errorString + ": " + bytes.utf8String)
      else
        throw new RuntimeException(errorString + ": " + bytes.utf8String)
    }
  }

  override def parseResponseBody[T](parse: Function[Array[Byte], T]) =
    withBodyParser((res: HttpResponse, bytes: ByteString) => new StrictResponse[T](res, parse.apply(bytes.toArray)))

  override def addQueryParameter(key: String, value: String): RequestBuilder[R] = {
    val query = request.getUri.query().withParam(key, value)
    val uriWithQuery = request.getUri.query(query)
    withRequest(request.withUri(uriWithQuery))
  }

  override def withRetry(retrySettings: RetrySettings): RequestBuilder[R] =
    copy(retrySettings = Some(retrySettings))

  override def withRetry(maxRetries: Int): RequestBuilder[R] =
    copy(retrySettings = Some(RetrySettings(maxRetries)))

  private def withBodyParser[T](parser: (HttpResponse, ByteString) => StrictResponse[T]): RequestBuilderImpl[T] =
    new RequestBuilderImpl[T](http, materializer, timeout, request, parser, retrySettings, sdkExecutor)

}
