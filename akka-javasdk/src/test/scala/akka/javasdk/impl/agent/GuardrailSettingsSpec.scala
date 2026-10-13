/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.javasdk.impl.ControlId
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

      "agent response guard" {
        class = "test.MyResponseGuard"
        agents = ["some-agent"]
        category = TOXIC
      }

      "wildcard guard" {
        class = "test.MyGuard"
        agents = ["some-agent"]
        category = TOXIC
        use-for = ["*"]
      }
    }
    """)
}

class GuardrailSettingsSpec extends AnyWordSpec with Matchers {
  import GuardrailSettingsSpec._

  "The GuardrailSettings" should {
    "load from config" in {
      val settings = GuardrailSettings(config.getConfig("akka.javasdk.agent.guardrails"))
      settings.configuredGuardrails.size shouldBe 4

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

      val third = settings.configuredGuardrails.find(_.name == "agent response guard").get
      third.useFor shouldBe empty

      val fourth = settings.configuredGuardrails.find(_.name == "wildcard guard").get
      fourth.useFor shouldBe Set(UseFor.Wildcard)
    }

    Seq("before-tool-call", "before-model-call", "before-agent-response").foreach { boundary =>
      s"reject use-for [$boundary]" in {
        val faulty = ConfigFactory.parseString(s"""
          "guard" {
            class = "test.MyGuard"
            agents = ["some-agent"]
            category = TOXIC
            use-for = ["$boundary"]
          }
          """)
        intercept[IllegalArgumentException] {
          GuardrailSettings(faulty)
        }.getMessage should include(s"Unknown use-for [$boundary]")
      }
    }

    "read the control id, and keep it in the config" in {
      val settings = GuardrailSettings(ConfigFactory.parseString("""
        "with id" { class = "test.MyGuard", category = TOXIC, control-id = "AI-GR-01" }
        "without id" { class = "test.MyGuard", category = TOXIC }
        """))

      settings.configuredGuardrails.map(g => g.name -> g.controlId).toMap shouldBe Map(
        "with id" -> Some("AI-GR-01"),
        "without id" -> None)
      settings.configuredGuardrails.find(_.name == "with id").get.config.getString("control-id") shouldBe "AI-GR-01"
    }

    Seq("\"\"", "\" \"").foreach { blank =>
      s"reject control-id = $blank" in {
        intercept[IllegalArgumentException] {
          GuardrailSettings(ConfigFactory.parseString(s"""
            "blank id" { class = "test.MyGuard", category = TOXIC, control-id = $blank }
            """))
        }.getMessage shouldBe "Guardrail [blank id] must define a non blank [control-id]"
      }
    }

    "reject a control id that is not a string" in {
      intercept[IllegalArgumentException] {
        GuardrailSettings(ConfigFactory.parseString("""
          "list id" { class = "test.MyGuard", category = TOXIC, control-id = ["AI-GR-01"] }
          """))
      }.getMessage shouldBe "Guardrail [list id] must define [control-id] as a string, but defines [LIST]"
    }

    "accept a control id at the length bound" in {
      val id = "C" * ControlId.MaxLength
      val settings = GuardrailSettings(ConfigFactory.parseString(s"""
        "long id" { class = "test.MyGuard", category = TOXIC, control-id = "$id" }
        """))
      settings.configuredGuardrails.find(_.name == "long id").get.controlId shouldBe Some(id)
    }

    "reject a control id longer than the length bound" in {
      val id = "C" * (ControlId.MaxLength + 1)
      intercept[IllegalArgumentException] {
        GuardrailSettings(ConfigFactory.parseString(s"""
          "long id" { class = "test.MyGuard", category = TOXIC, control-id = "$id" }
          """))
      }.getMessage shouldBe
      s"Guardrail [long id] must define a [control-id] of at most [${ControlId.MaxLength}] characters, but defines one of [${ControlId.MaxLength + 1}]"
    }

  }

}
