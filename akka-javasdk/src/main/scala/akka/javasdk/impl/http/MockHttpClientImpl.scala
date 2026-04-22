/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.function.{ Function => JFunction }

import scala.concurrent.duration.DurationInt
import scala.jdk.DurationConverters.JavaDurationOps

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpMethod
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.javasdk.http.HttpClient
import akka.javasdk.http.RequestBuilder
import akka.javasdk.http.StrictResponse
import akka.stream.SystemMaterializer
import akka.util.ByteString
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 *
 * HttpClient implementation that routes every request through a user-provided synchronous handler, executed on the SDK
 * dispatcher (virtual thread backed in the testkit).
 */
@InternalApi
private[akka] final class MockHttpClientImpl(
    system: ActorSystem[_],
    serviceName: String,
    handler: JFunction[HttpRequest, HttpResponse],
    sdkExecutor: Executor)
    extends HttpClient {

  private val log = LoggerFactory.getLogger(classOf[MockHttpClientImpl])

  private val baseUrl: String =
    if (serviceName.startsWith("http://") || serviceName.startsWith("https://")) serviceName
    else "http://" + serviceName

  private val http = Http(system)
  private val materializer = SystemMaterializer.get(system).materializer
  private val timeout =
    system.settings.config.getDuration("akka.http.server.request-timeout").toScala + 10.seconds

  private val requestSender: HttpRequest => CompletionStage[HttpResponse] = { request =>
    log.debug("Mocked HTTP request: {} {}", request.method.value, request.getUri)
    val promise = new CompletableFuture[HttpResponse]()
    sdkExecutor.execute(() =>
      try {
        val response = handler.apply(request)
        if (response == null)
          promise.completeExceptionally(
            new NullPointerException("HTTP mock handler returned null for request " + request.getUri))
        else {
          log.debug("Mocked HTTP response for {} {}: {}", request.method.value, request.getUri, response.status)
          promise.complete(response)
        }
      } catch {
        case ex: Throwable => promise.completeExceptionally(ex)
      })
    promise
  }

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
      req,
      None,
      new StrictResponse[ByteString](_, _),
      None,
      sdkExecutor,
      None,
      Some(requestSender))
  }
}
