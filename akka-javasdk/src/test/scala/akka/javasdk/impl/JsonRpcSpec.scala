/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.testkit.typed.TestException
import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.javasdk.impl.JsonRpc.JsonRpcEndpoint
import akka.javasdk.impl.JsonRpc.JsonRpcError
import akka.javasdk.impl.JsonRpc.JsonRpcErrorResponse
import akka.javasdk.impl.JsonRpc.JsonRpcRequest
import akka.javasdk.impl.JsonRpc.JsonRpcSuccessResponse
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpRequestHeaders
import akka.runtime.sdk.spi.JwtClaims
import akka.runtime.sdk.spi.RequestPrincipal
import akka.util.ByteString
import io.opentelemetry.api.trace.Span
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt

class JsonRpcSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with LogCapturing {

  private implicit val ec: ExecutionContext = system.executionContext

  "The JSON RPC implementation" should {

    "parse and render successful responses" in {
      val response = JsonRpcSuccessResponse(Some(1), "result")
      val bytes = JsonRpc.Serialization.responseToJsonBytes(response)
      val parsed = JsonRpc.Serialization.parseResponse(bytes.utf8String)
      parsed shouldEqual response
    }

    "parse and render error responses" in {
      val errorResponse = JsonRpcErrorResponse(Some(1), JsonRpcError(JsonRpcError.Codes.MethodNotFound, "result", None))
      val bytes = JsonRpc.Serialization.responseToJsonBytes(errorResponse)
      val parsed = JsonRpc.Serialization.parseResponse(bytes.utf8String)
      parsed shouldEqual errorResponse
    }

    "parse and render requests" in {
      val params = Map("a" -> 1)
      val request = JsonRpcRequest(method = "method1", params = Some(params), id = Some(1))
      val jsonString = JsonRpc.Serialization.requestToJsonString(request)
      val parsedSeq = JsonRpc.Serialization.parseRequest(ByteString(jsonString))
      parsedSeq shouldEqual Seq(request)
    }

    "parse batch requests" in {
      val parsedSeq = JsonRpc.Serialization.parseRequest(ByteString(
        """[{"jsonrpc":"2.0","method":"method1","params":{},"id":1},{"jsonrpc":"2.0","method":"method1","params":{},"id":2}]"""))
      parsedSeq should have size 2
    }

    "handle a single request" in {
      val endpoint = new JsonRpc.JsonRpcEndpoint(
        "example",
        Map("method1" -> { (request: JsonRpcRequest) =>
          Future.successful(JsonRpcSuccessResponse(request.id, "result"))
        }))

      val response = handle(
        endpoint,
        JsonRpc.Serialization.requestToJsonString(
          JsonRpcRequest(method = "method1", params = None, id = Some(1)))).futureValue
      response shouldBe """{"id":1,"result":"result"}"""
    }

    "respond with error for unknown method" in {
      val endpoint = new JsonRpc.JsonRpcEndpoint("example", Map.empty)

      val response = handle(
        endpoint,
        JsonRpc.Serialization.requestToJsonString(
          JsonRpcRequest(method = "method1", params = None, id = Some(1)))).futureValue
      response shouldBe """{"id":1,"error":{"code":-32601,"message":"Method [method1] not found"}}"""
    }

    "respond with error if handler throws" in {
      val endpoint = new JsonRpc.JsonRpcEndpoint(
        "example",
        Map("method1" -> { (_: JsonRpcRequest) =>
          throw TestException("Boom")
        }))

      val response = handle(
        endpoint,
        JsonRpc.Serialization.requestToJsonString(
          JsonRpcRequest(method = "method1", params = None, id = Some(1)))).futureValue
      response shouldBe """{"id":1,"error":{"code":-32603,"message":"Call caused internal error"}}"""
    }

    "respond with empty request is empty" in {
      val endpoint = new JsonRpc.JsonRpcEndpoint("example", Map.empty)

      val response = handle(endpoint, "").futureValue
      // and http accepted, but not checking here
      response shouldBe ""
    }

    // FIXME what about accepting notification and not responding?

    "handle a batch request" in {
      val endpoint = new JsonRpc.JsonRpcEndpoint(
        "example",
        Map("method1" -> { (request: JsonRpcRequest) =>
          Future.successful(JsonRpcSuccessResponse(request.id, "result"))
        }))

      val response = handle(
        endpoint,
        """[{"jsonrpc":"2.0","method":"method1","params":{},"id":1},{"jsonrpc":"2.0","method":"method1","params":{},"id":2}]""").futureValue
      response shouldBe """{"id":1,"result":"result"}{"id":2,"result":"result"}"""
    }

    "send responses to batch out as they arrive" in {
      val requestOneCompletion = Promise[String]()
      val requestTwoCompletion = Promise[String]()
      val requestProbe = createTestProbe[Option[Any]]()
      val endpoint = new JsonRpc.JsonRpcEndpoint(
        "example",
        Map("method1" -> { (request: JsonRpcRequest) =>
          requestProbe.ref ! request.id
          (if (request.id.contains(1)) {
             requestOneCompletion.future
           } else if (request.id.contains(2)) {
             requestTwoCompletion.future
           } else throw new IllegalArgumentException())
            .map(text => JsonRpcSuccessResponse(request.id, text))
        }))

      val futureHttpResponse = handle(
        endpoint,
        """[{"jsonrpc":"2.0","method":"method1","params":{},"id":1},{"jsonrpc":"2.0","method":"method1","params":{},"id":2}]""")

      requestProbe.receiveMessages(2)

      requestTwoCompletion.success("result 2")
      Thread.sleep(30)
      requestOneCompletion.success("result 1")

      futureHttpResponse.futureValue shouldBe """{"id":2,"result":"result 2"}{"id":1,"result":"result 1"}"""
    }
  }

  private def handle(endpoint: JsonRpcEndpoint, jsonRpcPayload: String): Future[String] = {
    val request = HttpRequest(
      uri = "http://example.com/example/method1",
      method = HttpMethods.POST,
      entity = HttpEntity(ContentTypes.`application/json`, jsonRpcPayload))
    val instance = endpoint.httpEndpointDescriptor
      .instanceFactory(new HttpEndpointConstructionContext {
        override def openTelemetrySpan: Option[Span] = None
        override def principal: RequestPrincipal = RequestPrincipal.empty
        override def jwt: Option[JwtClaims] = None
        override def requestHeaders: HttpRequestHeaders = new HttpRequestHeaders {
          override def header(name: String): Option[HttpHeader] = None
          override def allHeaders: Seq[HttpHeader] = Seq.empty
        }
        override def httpRequest: HttpRequest = request
      })
      .asInstanceOf[endpoint.HttpRequestHandler]

    val futureHttpResponse = instance.handle(request.entity.asInstanceOf[HttpEntity.Strict])

    futureHttpResponse.flatMap(response =>
      if (response.status.isSuccess()) response.entity.toStrict(3.seconds).map(_.data.utf8String)
      else fail("Unexpected response status " + response.status))
  }

}
