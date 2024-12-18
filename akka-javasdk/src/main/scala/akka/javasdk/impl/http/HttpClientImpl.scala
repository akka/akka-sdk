/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

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
import akka.http.javadsl.model.headers.HttpCredentials
import akka.javasdk.JsonSupport
import akka.javasdk.http.HttpClient
import akka.javasdk.http.RequestBuilder
import akka.javasdk.http.StrictResponse
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonProcessingException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.lang.{ Iterable => JIterable }
import java.nio.charset.Charset
import java.util.concurrent.CompletionStage
import java.util.function.Function

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.jdk.DurationConverters.JavaDurationOps

import akka.http.javadsl.model.StatusCodes

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class HttpClientImpl(
    http: Http,
    baseUrl: String,
    materializer: Materializer,
    timeout: FiniteDuration,
    defaultHeaders: Seq[HttpHeader])
    extends HttpClient {

  def this(system: ActorSystem[_], baseUrl: String, defaultHeaders: Seq[HttpHeader]) =
    this(
      Http(system),
      baseUrl,
      SystemMaterializer.get(system).materializer,
      // 10s higher than configured timeout, so configured timeout always win
      system.settings.config.getDuration("akka.http.server.request-timeout").toScala + 10.seconds,
      defaultHeaders)

  def this(system: ActorSystem[_], baseUrl: String) = this(system, baseUrl, Seq.empty)

  override def GET(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.GET)

  override def POST(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.POST)

  override def PUT(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.PUT)

  override def PATCH(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.PATCH)

  override def DELETE(uri: String): RequestBuilder[ByteString] = forMethod(uri, HttpMethods.DELETE)

  private def forMethod(uri: String, method: HttpMethod) = {
    val req = HttpRequest.create(baseUrl + uri).withMethod(method)
    new RequestBuilderImpl[ByteString](
      http,
      materializer,
      timeout,
      req.withHeaders(defaultHeaders.asJava),
      new StrictResponse[ByteString](_, _))
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
    bodyParser: (HttpResponse, ByteString) => StrictResponse[R])
    extends RequestBuilder[R] {

  override def withRequest(request: HttpRequest): RequestBuilder[R] = copy(request = request)

  override def addHeader(header: String, value: String): RequestBuilder[R] = addHeader(HttpHeader.parse(header, value))

  override def addHeader(header: HttpHeader): RequestBuilder[R] = withRequest(request.addHeader(header))

  override def withHeaders(headers: JIterable[HttpHeader]): RequestBuilder[R] = withRequest(
    request.withHeaders(headers))

  override def addCredentials(credentials: HttpCredentials): RequestBuilder[R] = withRequest(
    request.addCredentials(credentials))

  override def withTimeout(timeout: Duration) =
    new RequestBuilderImpl[R](http, materializer, timeout.toScala, request, bodyParser)

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

  override def invokeAsync: CompletionStage[StrictResponse[R]] = http
    .singleRequest(request)
    .thenCompose((response: HttpResponse) =>
      response.entity
        .toStrict(timeout.toMillis, materializer)
        .thenApply((entity: HttpEntity.Strict) => bodyParser.apply(response, entity.getData)))

  override def responseBodyAs[T](`type`: Class[T]) = new RequestBuilderImpl[T](
    http,
    materializer,
    timeout,
    request,
    { (res: HttpResponse, bytes: ByteString) =>
      try if (res.status.isFailure) {
        // FIXME should we have a better way to deal with failure?
        // FIXME what about error responses with a body, now we can't expect/parse those
        val errorString = "HTTP request for [" + request.getUri + "] failed with HTTP status " + res.status
        if (res.entity.getContentType.binary) throw new RuntimeException(errorString)
        else {
          if (res.status.intValue() == StatusCodes.BAD_REQUEST.intValue())
            throw new IllegalArgumentException(errorString + ": " + bytes.utf8String)
          else
            throw new RuntimeException(errorString + ": " + bytes.utf8String)
        }
      } else if (res.entity.getContentType == ContentTypes.APPLICATION_JSON)
        new StrictResponse[T](res, JsonSupport.decodeJson(`type`, bytes))
      else if (!res.entity.getContentType.binary && (`type` eq classOf[String]))
        new StrictResponse[T](
          res,
          new String(
            bytes.toArrayUnsafe(),
            res.entity.getContentType.getCharsetOption
              .map[Charset]((c: HttpCharset) => c.nioCharset)
              .orElse(StandardCharsets.UTF_8)).asInstanceOf[T])
      else
        throw new RuntimeException(
          "Expected to parse the response for " + request.getUri + " to " + `type` + " but response content type is " + res.entity.getContentType)
      catch {
        case e: IOException =>
          throw new RuntimeException(e)
      }
    })

  override def parseResponseBody[T](parse: Function[Array[Byte], T]) =
    new RequestBuilderImpl[T](
      http,
      materializer,
      timeout,
      request,
      (res: HttpResponse, bytes: ByteString) => new StrictResponse[T](res, parse.apply(bytes.toArray)))
}
