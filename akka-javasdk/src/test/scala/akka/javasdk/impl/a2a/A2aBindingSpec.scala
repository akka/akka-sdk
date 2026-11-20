/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import scala.jdk.CollectionConverters._

import akka.javasdk.JsonSupport
import akka.javasdk.a2a.A2aAgentCard
import akka.javasdk.a2a.A2aClientException
import akka.javasdk.a2a.A2aRemoteErrorException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class A2aBindingSpec extends AnyWordSpec with Matchers {

  private def card(interfaces: (String, String, String)*): A2aAgentCard =
    new A2aAgentCard(
      "agent",
      null,
      interfaces.map { case (url, binding, version) => new A2aAgentCard.Interface(url, binding, null, version) }.asJava,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null)

  "Binding selection" should {

    "prefer JSON-RPC when the card lists both" in {
      A2aBinding.select(card(("https://a/rest", "HTTP+JSON", "1.0"), ("https://a/rpc", "JSONRPC", "1.0"))) shouldBe
      A2aBinding.JsonRpc("https://a/rpc", None)
    }

    "fall back to HTTP+JSON" in {
      A2aBinding.select(card(("https://a/rest", "HTTP+JSON", "1.0"), ("a:9000", "GRPC", "1.0"))) shouldBe
      A2aBinding.HttpJson("https://a/rest", None)
    }

    "carry the tenant of the interface" in {
      val withTenant = new A2aAgentCard(
        "agent",
        null,
        java.util.List.of(new A2aAgentCard.Interface("https://a/rpc", "JSONRPC", "acme", "1.0")),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null)
      A2aBinding.select(withTenant) shouldBe A2aBinding.JsonRpc("https://a/rpc", Some("acme"))
    }

    "skip an interface for another protocol version" in {
      A2aBinding.select(card(("https://a/old", "JSONRPC", "0.3"), ("https://a/rest", "HTTP+JSON", "1.0"))) shouldBe
      A2aBinding.HttpJson("https://a/rest", None)
    }

    "take an interface without a version" in {
      A2aBinding.select(card(("https://a/rpc", "jsonrpc", null))) shouldBe A2aBinding.JsonRpc("https://a/rpc", None)
    }

    "fail when the card offers only other bindings" in {
      val e = intercept[A2aClientException](A2aBinding.select(card(("a:9000", "GRPC", "1.0"))))
      e.getMessage should include("GRPC")
      e.getMessage should include("JSONRPC")
    }
  }

  "Error mapping" should {

    "map a JSON-RPC error code to its A2A reason" in {
      val e = A2aBinding.jsonRpcError(JsonRpcError.of(A2aErrorCode.TASK_NOT_FOUND))
      e.code shouldBe -32001
      e.reason shouldBe "TASK_NOT_FOUND"
      e.getMessage should include("Task not found")
    }

    "keep the code and message of a JSON-RPC error that has no A2A reason" in {
      val e = A2aBinding.jsonRpcError(new JsonRpcError(-32099, "custom", null))
      e.code shouldBe -32099
      e.reason shouldBe null
      e.getMessage shouldBe "custom"
    }

    "read the reason from the ErrorInfo of a JSON-RPC error" in {
      val data = JsonSupport.getObjectMapper.readTree(
        """[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"CONTENT_TYPE_NOT_SUPPORTED","domain":"a2a-protocol.org"}]""")
      A2aBinding.jsonRpcError(new JsonRpcError(-32000, "bad", data)).reason shouldBe "CONTENT_TYPE_NOT_SUPPORTED"
    }

    "map an HTTP+JSON error by its ErrorInfo reason to the A2A code" in {
      val body = JsonSupport.getObjectMapper.readTree(
        """{"error":{"code":404,"status":"NOT_FOUND","message":"no such task","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"TASK_NOT_FOUND","domain":"a2a-protocol.org"}]}}""")
      val e = A2aBinding.httpJsonError(404, Some(body))
      e shouldBe a[A2aRemoteErrorException]
      e.asInstanceOf[A2aRemoteErrorException].code shouldBe -32001
      e.asInstanceOf[A2aRemoteErrorException].reason shouldBe "TASK_NOT_FOUND"
    }

    "report a plain HTTP failure as a client exception" in {
      val e = A2aBinding.httpJsonError(502, None)
      e should not be a[A2aRemoteErrorException]
      e.getMessage should include("502")
    }

    "ignore an ErrorInfo of another domain" in {
      val body = JsonSupport.getObjectMapper.readTree(
        """{"error":{"message":"x","details":[{"reason":"TASK_NOT_FOUND","domain":"other.example"}]}}""")
      A2aBinding.httpJsonError(400, Some(body)) should not be a[A2aRemoteErrorException]
    }
  }
}
