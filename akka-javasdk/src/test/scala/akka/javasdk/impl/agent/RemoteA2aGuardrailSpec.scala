/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.ToolCallGuardrail.ToolOrigin
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object RemoteA2aGuardrailSpec {
  private val tracer: () => Tracer = () => OpenTelemetry.noop().getTracer("test")

  private val cardUrl = "https://claims.example.com/.well-known/agent-card.json"

  private def config(guardrails: String) =
    ConfigFactory.parseString(s"""
      akka.javasdk.a2a.remote-agents.claims.card-url = "$cardUrl"
      akka.javasdk.agent.guardrails {
        $guardrails
      }
      """)

  private def guard(name: String, cls: String, extra: String) =
    s""""$name" {
         class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$$cls"
         agents = ["caller"]
         category = TOOL_POLICY
         $extra
       }"""

  private def a2a(agent: String = "claims", url: String = cardUrl) = new ToolOrigin.RemoteA2a(agent, url, "pay")
}

class RemoteA2aGuardrailSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with LogCapturing {
  import RemoteA2aGuardrailSpec._

  private def provider(guardrails: String) = new GuardrailProvider(system, config(guardrails), tracer)

  private def names(guardrails: String, origin: ToolOrigin, toolName: String = "claims_pay") =
    provider(guardrails)
      .agentGuardrails("caller", None)
      .toolCallGuardrailEntries(toolName, origin)
      .map(_.configuredGuardrail.name)

  private def evaluate(guardrails: String): Try[Unit] = {
    implicit val ec: ExecutionContext = system.executionContext
    val entries = provider(guardrails).agentGuardrails("caller", None).toolCallGuardrailEntries("claims_pay", a2a())
    val ctx =
      new GuardrailProvider.ToolCallGuardrailCallContextImpl("caller", "claims_pay", "", "{}", "", a2a(), None, tracer)
    Try(Await.result(GuardrailProvider.evaluateToolCall(entries, ctx), 3.seconds))
  }

  "Guardrail scoping by tool origin and remote agent" should {

    "select by tool-origins" in {
      val g = guard("a2a only", "MyToolGuard", "tool-origins = [remote-a2a]")
      names(g, a2a()) shouldBe Seq("a2a only")
      names(g, new ToolOrigin.FunctionTool()) shouldBe empty
      names(g, new ToolOrigin.RemoteMcp("http://mcp")) shouldBe empty
    }

    "select remote agents by name and by card URL" in {
      val byName = guard("by name", "MyToolGuard", """remote-agents = ["claims"]""")
      val byUrl = guard("by url", "MyToolGuard", s"""remote-agents = ["$cardUrl/"]""")
      names(byName, a2a()) shouldBe Seq("by name")
      names(byName, a2a(agent = "other")) shouldBe empty
      names(byUrl, a2a(agent = "other")) shouldBe Seq("by url")
      names(byUrl, a2a(url = "https://other.example.com/card")) shouldBe empty
      names(byName, new ToolOrigin.FunctionTool()) shouldBe empty
    }

    "keep the attached tool guardrails of function and MCP tools out of an A2A-only entry" in {
      val g = provider(guard("a2a only", "MyToolGuard", "tool-origins = [remote-a2a]"))
        .agentGuardrails("caller", None)
      g.toolCallGuardrails("some-tool") shouldBe empty
      g.mcpToolCallGuardrails("http://mcp", "some-tool") shouldBe empty
      g.toolCallGuardrails("claims_pay", Some(a2a())).map(_.settings.name) shouldBe Seq("a2a only")
    }

    "refuse an unknown tool origin" in {
      val e = intercept[IllegalArgumentException](names(guard("g", "MyToolGuard", "tool-origins = [mcp]"), a2a()))
      e.getMessage should include("Unknown tool-origins value [mcp]")
    }

    "refuse remote-agents with tool-origins that leave out remote-a2a" in {
      val e = intercept[IllegalArgumentException](
        names(guard("g", "MyToolGuard", """tool-origins = [function-tool], remote-agents = ["claims"]"""), a2a()))
      e.getMessage should include("leave out remote-a2a")
    }

    "refuse a remote agent that is not configured" in {
      val e =
        intercept[IllegalArgumentException](names(guard("g", "MyToolGuard", """remote-agents = ["claimz"]"""), a2a()))
      e.getMessage should include("[claimz]")
    }

    "refuse the scoping keys on a guardrail that is not a ToolCallGuardrail" in {
      val e =
        intercept[IllegalArgumentException](names(guard("g", "MyResponseGuard", "tool-origins = [remote-a2a]"), a2a()))
      e.getMessage should include("only a [akka.javasdk.agent.ToolCallGuardrail] takes")
    }
  }

  "Evaluating ToolCallGuardrails outside the agent loop" should {

    "fail with a GuardrailException on a deny" in {
      val result = evaluate(guard("no", "MyToolGuard", ""))
      result.failed.get shouldBe a[Guardrail.GuardrailException]
      result.failed.get.getMessage shouldBe "Guardrail [no] denied the call: no says no"
    }

    "let the call continue on a report-only deny" in {
      evaluate(guard("no", "MyToolGuard", "report-only = true")).isSuccess shouldBe true
    }

    "let the call continue on an allow" in {
      evaluate(guard("yes", "AllowingToolGuard", "")).isSuccess shouldBe true
    }

    "fail closed when the guardrail throws, and open with fail-open" in {
      val closed = evaluate(guard("boom", "ThrowingToolGuard", ""))
      closed.failed.get shouldBe a[Guardrail.GuardrailException]
      closed.failed.get.getMessage should include("kaboom")
      evaluate(guard("boom", "ThrowingToolGuard", "fail-mode = fail-open")).isSuccess shouldBe true
    }
  }
}
