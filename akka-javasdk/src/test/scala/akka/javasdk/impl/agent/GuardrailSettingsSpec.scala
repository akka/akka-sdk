/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.javasdk.impl.agent.ConfiguredGuardrail.UseFor
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object GuardrailSettingsSpec {
  private val config = ConfigFactory.parseString(s"""
    akka.javasdk.agent.guardrails {
      "request prompt injection" {
        class = "akka.javasdk.agent.SimilarityGuard"
        agents = ["planner-agent", "evaluator-agent"]
        category = PROMPT_INJECTION
        use-for = ["model-request"]
        threshold = 0.72
        bad-examples-resource-dir = "guardrail/jailbreak"
      }

      "my guard" {
        class = "test.MyGuard"
        agent-roles = ["worker"]                      
        category = TOXIC
        use-for = ["model-response", "mcp-tool-response"]
        report-only = true
        some-other-property = "foo"
      }
    }
    """)
}

class GuardrailSettingsSpec extends AnyWordSpec with Matchers {
  import GuardrailSettingsSpec._

  "The GuardrailSettings" should {
    "load from config" in {
      val settings = GuardrailSettings(config.getConfig("akka.javasdk.agent.guardrails"))
      settings.configuredGuardrails.size shouldBe 2

      val first = settings.configuredGuardrails.find(_.name == "request prompt injection").get
      first.implementationClass shouldBe "akka.javasdk.agent.SimilarityGuard"
      first.agents shouldBe Set("planner-agent", "evaluator-agent")
      first.agentRoles shouldBe Set.empty
      first.useFor shouldBe Set(UseFor.ModelRequest)
      first.config.getDouble("threshold") shouldBe 0.72
      first.config.getString("bad-examples-resource-dir") shouldBe "guardrail/jailbreak"

      val second = settings.configuredGuardrails.find(_.name == "my guard").get
      second.implementationClass shouldBe "test.MyGuard"
      second.agents shouldBe Set.empty
      second.agentRoles shouldBe Set("worker")
      second.useFor shouldBe Set(UseFor.ModelResponse, UseFor.McpToolResponse)
      second.config.getString("some-other-property") shouldBe "foo"
    }

  }

}
