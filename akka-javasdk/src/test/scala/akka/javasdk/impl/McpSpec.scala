/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpRequestHeaders
import akka.runtime.sdk.spi.JwtClaims
import akka.runtime.sdk.spi.RequestPrincipal
import akka.util.ByteString
import io.opentelemetry.api.trace.Span
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.CompletableFuture
import scala.concurrent.duration.DurationInt
import scala.jdk.FutureConverters.CompletionStageOps

class McpSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with LogCapturing {

  "The stateless MCP server" should {
    "accept init request" in {
      val httpEndpointDescriptor = new Mcp.StatelessMcpEndpoint(Seq.empty).httpEndpoint()
      httpEndpointDescriptor.methods should have size 1

      val initializeRequest = Mcp.InitializeRequest(
        protocolVersion = Mcp.ProtocolVersion,
        capabilities = Mcp.ClientCapabilities(experimental = Map.empty, roots = None, sampling = Map.empty),
        clientInfo = Mcp.Implementation(name = "McpSpec", version = "2000"),
        meta = None)
      val jsonRpcRequest = Mcp.request(Mcp.InitializeRequest.method, Some(initializeRequest))
      val requestString = JsonRpc.Serialization.requestToJsonString(jsonRpcRequest)

      val strictEntity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString(requestString))
      val clientHttpRequest =
        HttpRequest(uri = "http://localhost/mpc", entity = strictEntity)

      val endpointInstance = httpEndpointDescriptor.instanceFactory(new HttpEndpointConstructionContext {
        override def openTelemetrySpan: Option[Span] = None
        override def principal: RequestPrincipal = RequestPrincipal.empty
        override def jwt: Option[JwtClaims] = None
        override def requestHeaders: HttpRequestHeaders = new HttpRequestHeaders {
          override def header(name: String): Option[HttpHeader] = None
          override def allHeaders: Seq[HttpHeader] = Seq.empty
        }

        override def httpRequest: HttpRequest = clientHttpRequest
      })

      val httpResponse = httpEndpointDescriptor.methods.head.userMethod
        .invoke(endpointInstance, strictEntity)
        .asInstanceOf[CompletableFuture[HttpResponse]]
        .asScala
        .futureValue

      httpResponse.entity.contentType should be(ContentTypes.`application/json`)
      val responseJsonText = httpResponse.entity.toStrict(3.seconds).futureValue.data.utf8String
      val response = JsonRpc.Serialization.parseResponse(responseJsonText)
      response.id should be(jsonRpcRequest.id)
      val mcpResult = Mcp.result[Mcp.InitializeResult](response)

      mcpResult.protocolVersion should be(Mcp.ProtocolVersion)
    }
  }

}
