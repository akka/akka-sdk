/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.javasdk.agent.RemoteMcpTools
import akka.javasdk.impl.agent.GuardrailProvider.AgentGuardrails
import akka.runtime.sdk.spi.SpiAgent
import io.opentelemetry.api.OpenTelemetry
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentImplMcpToolInterceptorSpec extends AnyWordSpec with Matchers {

  private final class RecordingInterceptor extends RemoteMcpTools.ToolInterceptor {
    @volatile var requestContext: RemoteMcpTools.ToolInterceptorContext = _
    @volatile var responseContext: RemoteMcpTools.ToolInterceptorContext = _
    @volatile var responseArguments: String = _

    override def interceptRequest(
        context: RemoteMcpTools.ToolInterceptorContext,
        requestPayloadJson: String): String = {
      requestContext = context
      """{"city":"Leuven"}"""
    }

    override def interceptResponse(
        context: RemoteMcpTools.ToolInterceptorContext,
        requestPayloadJson: String,
        responsePayload: String): String = {
      responseContext = context
      responseArguments = requestPayloadJson
      s"intercepted: $responsePayload"
    }
  }

  private def hooksFor(interceptor: RemoteMcpTools.ToolInterceptor): SpiAgent.McpToolCallHooks = {
    val guardrails = new AgentGuardrails(Nil, () => OpenTelemetry.noop().getTracer("test"))
    val endpoints = AgentImpl.toSpiMcpEndpoints(
      Seq(RemoteMcpTools.fromServer("http://localhost:1234/mcp").withToolInterceptor(interceptor)),
      guardrails)
    endpoints.head.toolInterceptor match {
      case Some(hooks: SpiAgent.McpToolCallHooks) => hooks
      case other                                  => fail(s"expected McpToolCallHooks, got [$other]")
    }
  }

  private val request = new SpiAgent.ToolCallRequest("call-1", "weather", """{"city":"Brussels"}""")

  "The MCP tool interceptor from AgentImpl.toSpiMcpEndpoints" should {

    "give interceptResponse the error flag of the result and keep it in the returned result" in {
      val interceptor = new RecordingInterceptor
      val hooks = hooksFor(interceptor)

      val arguments = Await.result(hooks.interceptRequest(request), 3.seconds)
      arguments shouldBe """{"city":"Leuven"}"""
      interceptor.requestContext.toolName() shouldBe "weather"
      interceptor.requestContext.isError() shouldBe false

      val result = Await.result(
        hooks.interceptResponse(
          request,
          arguments,
          new SpiAgent.McpToolCallResult("service unavailable", isError = true)),
        3.seconds)
      interceptor.responseContext.toolName() shouldBe "weather"
      interceptor.responseContext.isError() shouldBe true
      interceptor.responseArguments shouldBe arguments
      result.text shouldBe "intercepted: service unavailable"
      result.isError shouldBe true
    }

    "give interceptResponse a false error flag for a successful result" in {
      val interceptor = new RecordingInterceptor
      val hooks = hooksFor(interceptor)

      val result = Await.result(
        hooks.interceptResponse(request, request.arguments, new SpiAgent.McpToolCallResult("sunny", isError = false)),
        3.seconds)
      interceptor.responseContext.isError() shouldBe false
      result.text shouldBe "intercepted: sunny"
      result.isError shouldBe false
    }

    "keep the request and the result when the default interceptor methods leave them unchanged" in {
      val hooks = hooksFor(new RemoteMcpTools.ToolInterceptor {})
      val result = new SpiAgent.McpToolCallResult("failed", isError = true)

      (Await.result(hooks.interceptRequest(request), 3.seconds) should be).theSameInstanceAs(request.arguments)
      (Await.result(hooks.interceptResponse(request, request.arguments, result), 3.seconds) should be)
        .theSameInstanceAs(result)
    }

    "fail the returned future when the interceptor throws" in {
      val hooks = hooksFor(new RemoteMcpTools.ToolInterceptor {
        override def interceptRequest(
            context: RemoteMcpTools.ToolInterceptorContext,
            requestPayloadJson: String): String =
          throw new IllegalArgumentException("denied")
      })

      val failure = hooks.interceptRequest(request)
      Await.ready(failure, 3.seconds)
      failure.value.get.failed.get shouldBe an[IllegalArgumentException]
    }
  }
}
