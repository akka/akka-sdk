/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.javasdk.impl.JsonRpc.JsonRpcRequest
import akka.javasdk.impl.JsonRpc.JsonRpcSuccessResponse
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpRequestHeaders
import akka.runtime.sdk.spi.JwtClaims
import akka.runtime.sdk.spi.RequestPrincipal
import io.opentelemetry.api.trace.Span
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class JsonRpcSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {

  "The JSON RPC implementation" should {
    "handle a single request" in {
      val endpoint = new JsonRpc.JsonRpcEndpoint(
        "example",
        Map("method1" -> { (request: JsonRpcRequest) =>
          Future.successful(JsonRpcSuccessResponse(request.id, "result"))
        }))(scala.concurrent.ExecutionContext.global)

      val httpRequestIn =
        HttpRequest(
          uri = "http://example.com/example/method1",
          method = HttpMethods.POST,
          entity =
            HttpEntity(ContentTypes.`application/json`, """{"jsonrpc":"2.0","method":"method1","params":{},"id":1}"""))

      val instance = endpoint.httpEndpointDescriptor
        .instanceFactory(new HttpEndpointConstructionContext {
          override def openTelemetrySpan: Option[Span] = None
          override def principal: RequestPrincipal = RequestPrincipal.empty
          override def jwt: Option[JwtClaims] = None
          override def requestHeaders: HttpRequestHeaders = new HttpRequestHeaders {
            override def header(name: String): Option[HttpHeader] = None
            override def allHeaders: Seq[HttpHeader] = Seq.empty
          }
          override def httpRequest: HttpRequest = httpRequestIn
        })
        .asInstanceOf[endpoint.HttpRequestHandler]

      val httpResponse = instance.handle(httpRequestIn.entity.asInstanceOf[HttpEntity.Strict]).futureValue
      val responsePayload = httpResponse.entity
        .toStrict(3.seconds)
        .futureValue
        .data

      responsePayload.utf8String shouldBe """{"id":1,"result":"result"}"""
    }

    "handle a batch request" in {
      val endpoint = new JsonRpc.JsonRpcEndpoint(
        "example",
        Map("method1" -> { (request: JsonRpcRequest) =>
          Future.successful(JsonRpcSuccessResponse(request.id, "result"))
        }))(scala.concurrent.ExecutionContext.global)

      val httpRequestIn =
        HttpRequest(
          uri = "http://example.com/example/method1",
          method = HttpMethods.POST,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            """[{"jsonrpc":"2.0","method":"method1","params":{},"id":1},{"jsonrpc":"2.0","method":"method1","params":{},"id":2}]"""))

      val instance = endpoint.httpEndpointDescriptor
        .instanceFactory(new HttpEndpointConstructionContext {
          override def openTelemetrySpan: Option[Span] = None
          override def principal: RequestPrincipal = RequestPrincipal.empty
          override def jwt: Option[JwtClaims] = None
          override def requestHeaders: HttpRequestHeaders = new HttpRequestHeaders {
            override def header(name: String): Option[HttpHeader] = None
            override def allHeaders: Seq[HttpHeader] = Seq.empty
          }
          override def httpRequest: HttpRequest = httpRequestIn
        })
        .asInstanceOf[endpoint.HttpRequestHandler]

      val httpResponse = instance.handle(httpRequestIn.entity.asInstanceOf[HttpEntity.Strict]).futureValue
      val responsePayload = httpResponse.entity
        .toStrict(3.seconds)
        .futureValue
        .data

      responsePayload.utf8String shouldBe """{"id":1,"result":"result"}{"id":2,"result":"result"}"""
    }
  }

}
