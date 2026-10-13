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

object AgentImplMcpToolsSpec {

  private val InterceptedArguments = """{"echo":"intercepted"}"""

  // Records what each hook receives.
  final class RecordingInterceptor extends RemoteMcpTools.ToolInterceptor {
    @volatile var requestToolName: String = _
    @volatile var responseToolName: String = _
    @volatile var responseArguments: String = _

    override def interceptRequest(
        context: RemoteMcpTools.ToolInterceptorContext,
        requestPayloadJson: String): String = {
      requestToolName = context.toolName()
      InterceptedArguments
    }

    override def interceptResponse(
        context: RemoteMcpTools.ToolInterceptorContext,
        requestPayloadJson: String,
        responsePayload: String): String = {
      responseToolName = context.toolName()
      responseArguments = requestPayloadJson
      s"intercepted $responsePayload"
    }
  }

  final class ThrowingInterceptor extends RemoteMcpTools.ToolInterceptor {
    override def interceptRequest(context: RemoteMcpTools.ToolInterceptorContext, requestPayloadJson: String): String =
      throw new IllegalStateException("request failed")

    override def interceptResponse(
        context: RemoteMcpTools.ToolInterceptorContext,
        requestPayloadJson: String,
        responsePayload: String): String =
      throw new IllegalStateException("response failed")
  }
}

class AgentImplMcpToolsSpec extends AnyWordSpec with Matchers {
  import AgentImplMcpToolsSpec._

  private val noGuardrails = new AgentGuardrails(Seq.empty, () => OpenTelemetry.noop().getTracer("test"))

  private val request = new SpiAgent.ToolCallRequest("call-1", "echo", """{"echo":"hi"}""")

  private def hooksFor(interceptor: RemoteMcpTools.ToolInterceptor): SpiAgent.McpToolCallHooks = {
    val mcpTools = new RemoteMcpToolsImpl("http://example.com/mcp").withToolInterceptor(interceptor)

    AgentImpl.toSpiMcpEndpoints(Seq(mcpTools), noGuardrails).head.toolInterceptor match {
      case Some(hooks: SpiAgent.McpToolCallHooks) => hooks
      case other                                  => fail(s"expected McpToolCallHooks, got [$other]")
    }
  }

  "AgentImpl.toSpiMcpEndpoints" should {

    "use http://<service-name>/mcp as the endpoint of RemoteMcpTools.fromService" in {
      val endpoint = AgentImpl.toSpiMcpEndpoints(Seq(RemoteMcpTools.fromService("payments")), noGuardrails).head

      endpoint.mcpEndpoint shouldBe "http://payments/mcp"
    }

    "give interceptRequest the tool name and return the arguments it returns" in {
      val interceptor = new RecordingInterceptor
      val hooks = hooksFor(interceptor)

      Await.result(hooks.interceptRequest(request), 3.seconds) shouldBe InterceptedArguments
      interceptor.requestToolName shouldBe "echo"
    }

    "give interceptResponse the arguments that were sent and keep the error flag" in {
      val interceptor = new RecordingInterceptor
      val hooks = hooksFor(interceptor)

      val result = Await.result(
        hooks.interceptResponse(request, InterceptedArguments, new SpiAgent.McpToolCallResult("boom", isError = true)),
        3.seconds)

      result.text shouldBe "intercepted boom"
      result.isError shouldBe true
      interceptor.responseToolName shouldBe "echo"
      interceptor.responseArguments shouldBe InterceptedArguments
    }

    "return a failed Future when the interceptor throws" in {
      val hooks = hooksFor(new ThrowingInterceptor)

      val requestResult = hooks.interceptRequest(request)
      val responseResult =
        hooks.interceptResponse(request, InterceptedArguments, new SpiAgent.McpToolCallResult("ok", isError = false))

      Await.result(requestResult.failed, 3.seconds).getMessage shouldBe "request failed"
      Await.result(responseResult.failed, 3.seconds).getMessage shouldBe "response failed"
    }
  }
}
