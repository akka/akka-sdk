/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.Decision
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.GuardrailContext
import akka.javasdk.agent.ModelGuardrail
import akka.javasdk.agent.ModelGuardrailContext
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
import akka.javasdk.agent.ToolGuardrail
import akka.javasdk.agent.ToolGuardrailContext
import akka.runtime.sdk.spi.SpiAgent
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object GuardrailProviderSpec {
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
        class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
        agent-roles = ["worker"]                      
        category = TOXIC
        use-for = ["model-response", "mcp-tool-response"]
        report-only = true
        some-other-property = "foo"
      }
    }
    """)

  @nowarn("cat=deprecation")
  class MyGuard extends TextGuardrail {

    override def evaluate(text: String): Guardrail.Result =
      new Guardrail.Result(true, "")
  }

  @nowarn("cat=deprecation")
  class AnotherGuard(context: GuardrailContext) extends TextGuardrail {

    override def evaluate(text: String): Guardrail.Result =
      new Guardrail.Result(false, s"${context.name} says no")
  }

  class MyToolGuard(context: GuardrailContext) extends ToolGuardrail {
    override def evaluate(ctx: ToolGuardrailContext): Decision =
      Decision.deny(s"${context.name} says no")
  }

  class MyModelGuard(context: GuardrailContext) extends ModelGuardrail {
    override def evaluate(ctx: ModelGuardrailContext): Decision =
      Decision.deny(s"${context.name} says no")
  }

  class BothGuard extends ToolGuardrail with ModelGuardrail {
    override def evaluate(ctx: ToolGuardrailContext): Decision = Decision.allow()
    override def evaluate(ctx: ModelGuardrailContext): Decision = Decision.allow()
  }

  class FailingModelGuard extends ModelGuardrail {
    val cause = new IllegalStateException("upstream classifier unreachable")
    override def evaluate(ctx: ModelGuardrailContext): Decision =
      Decision.error("evaluation failed", cause)
  }

  class ThrowingModelGuard extends ModelGuardrail {
    override def evaluate(ctx: ModelGuardrailContext): Decision =
      throw new IllegalStateException("kaboom")
  }

  class ThrowingToolGuard extends ToolGuardrail {
    override def evaluate(ctx: ToolGuardrailContext): Decision =
      throw new IllegalStateException("kaboom")
  }

  class WrongGuard
}

class GuardrailProviderSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with LogCapturing {
  import GuardrailProviderSpec._

  "The GuardrailProvider" should {
    "validate" in {
      val provider = new GuardrailProvider(system, config)
      provider.validate()
    }

    "throw from validate when wrong Guardrail class" in {
      val faultyConfig =
        ConfigFactory
          .parseString("""
          akka.javasdk.agent.guardrails {
            "my guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$WrongGuard"
            }
          }
          """)
          .withFallback(config)
      val provider = new GuardrailProvider(system, faultyConfig)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("must implement [akka.javasdk.agent.Guardrail]")
    }

    "throw from validate when wrong config" in {
      val faultyConfig =
        ConfigFactory
          .parseString("""
          akka.javasdk.agent.guardrails {
            "request prompt injection" {
              threshold = wrong-double
            }
          }
          """)
          .withFallback(config)
      val provider = new GuardrailProvider(system, faultyConfig)
      intercept[ConfigException] {
        provider.validate()
      }.getMessage should include("threshold has type STRING rather than NUMBER")
    }

    "select guardrails for an agent" in {
      val provider = new GuardrailProvider(system, config)

      val g1 = provider.agentGuardrails("planner-agent", role = None)
      g1.entries.size shouldBe 1
      g1.entries.head.configuredGuardrail.name shouldBe "request prompt injection"
      g1.entries.head.guardrail.getClass shouldBe classOf[SimilarityGuard]
      g1.modelRequestGuardrails.size shouldBe 1
      g1.modelRequestGuardrails.head.getClass shouldBe classOf[SpiAgent.SimilarityGuard]
      g1.modelRequestGuardrails.head.asInstanceOf[SpiAgent.SimilarityGuard].category shouldBe "PROMPT_INJECTION"
      g1.modelResponseGuardrails shouldBe empty

      val g2 = provider.agentGuardrails("planner-agent", role = Some("worker"))
      g2.entries.size shouldBe 2
      g2.modelRequestGuardrails.size shouldBe 1
      g2.modelRequestGuardrails.head.getClass shouldBe classOf[SpiAgent.SimilarityGuard]
      g2.modelResponseGuardrails.size shouldBe 1
      g2.modelResponseGuardrails.head.name shouldBe "my guard"
    }

    "select guardrails with wildcards" in {
      val wildcardConfig = ConfigFactory
        .parseString(s"""
        akka.javasdk.agent.guardrails {
          "componentId wildcard guard" {
            class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AnotherGuard"
            agents = ["*"]
            category = TOXIC
            use-for = ["*"]
          }
          "role wildcard guard" {
            class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AnotherGuard"
            agent-roles = ["*"]
            category = TOXIC
            use-for = ["*"]
          }
          "componentId and role wildcard guard" {
            class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AnotherGuard"
            agents = ["*", "summarizer-agent"]
            agent-roles = ["*", "author"]
            category = TOXIC
            use-for = ["*"]
          }
        }
        """)
        .withFallback(config)
      val provider = new GuardrailProvider(system, wildcardConfig)

      val g1 = provider.agentGuardrails("planner-agent", role = None)
      g1.entries.map(_.configuredGuardrail.name) should contain theSameElementsAs Set(
        "request prompt injection",
        "componentId wildcard guard",
        "componentId and role wildcard guard")

      val g2 = provider.agentGuardrails("weather-agent", role = Some("worker"))
      g2.entries.map(_.configuredGuardrail.name) should contain theSameElementsAs Set(
        "my guard",
        "componentId wildcard guard",
        "role wildcard guard",
        "componentId and role wildcard guard")
    }

    "register a ToolGuardrail and produce a working SpiAgent.Guardrail" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "my tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
              use-for = ["mcp-tool-request"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg)
      val g = provider.agentGuardrails("tool-agent", role = None)
      g.mcpToolRequestGuardrails.size shouldBe 1
      g.modelRequestGuardrails shouldBe empty

      val spiGuardrail = g.mcpToolRequestGuardrails.head
      spiGuardrail.name shouldBe "my tool guard"
      spiGuardrail.category shouldBe "TOOL_POLICY"

      val result =
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything")), 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "my tool guard says no"
    }

    "register a ModelGuardrail and produce a working SpiAgent.Guardrail" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "my model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["model-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg)
      val g = provider.agentGuardrails("model-agent", role = None)
      g.modelResponseGuardrails.size shouldBe 1
      g.mcpToolRequestGuardrails shouldBe empty

      val spiGuardrail = g.modelResponseGuardrails.head
      spiGuardrail.name shouldBe "my model guard"
      spiGuardrail.category shouldBe "MODEL_POLICY"

      val result =
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything")), 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "my model guard says no"
    }

    "translate a Decision.Error into a failed Future preserving reason and cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "failing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$FailingModelGuard"
              agents = ["failing-agent"]
              category = MODEL_POLICY
              use-for = ["model-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg)
      val g = provider.agentGuardrails("failing-agent", role = None)
      val spiGuardrail = g.modelResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything")), 3.seconds)
      }
      failure.getMessage shouldBe "evaluation failed"
      failure.getCause shouldBe a[IllegalStateException]
      failure.getCause.getMessage shouldBe "upstream classifier unreachable"
    }

    "translate a thrown exception from a ModelGuardrail into a failed Future preserving the throwable as cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "throwing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$ThrowingModelGuard"
              agents = ["throwing-agent"]
              category = MODEL_POLICY
              use-for = ["model-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg)
      val g = provider.agentGuardrails("throwing-agent", role = None)
      val spiGuardrail = g.modelResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything")), 3.seconds)
      }
      failure.getMessage shouldBe "kaboom"
      failure.getCause shouldBe a[IllegalStateException]
      failure.getCause.getMessage shouldBe "kaboom"
    }

    "translate a thrown exception from a ToolGuardrail into a failed Future preserving the throwable as cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "throwing tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$ThrowingToolGuard"
              agents = ["throwing-tool-agent"]
              category = TOOL_POLICY
              use-for = ["mcp-tool-request"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg)
      val g = provider.agentGuardrails("throwing-tool-agent", role = None)
      val spiGuardrail = g.mcpToolRequestGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything")), 3.seconds)
      }
      failure.getMessage shouldBe "kaboom"
      failure.getCause shouldBe a[IllegalStateException]
      failure.getCause.getMessage shouldBe "kaboom"
    }

    "throw from validate when a class implements both ToolGuardrail and ModelGuardrail" in {
      val faultyConfig =
        ConfigFactory
          .parseString(s"""
          akka.javasdk.agent.guardrails {
            "both guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$BothGuard"
              agents = ["some-agent"]
              category = MIXED
              use-for = ["model-response"]
            }
          }
          """)
          .withFallback(config)
      val provider = new GuardrailProvider(system, faultyConfig)
      val message = intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage
      message should include(classOf[ToolGuardrail].getName)
      message should include(classOf[ModelGuardrail].getName)
    }

    "throw from validate when a ToolGuardrail is bound to a model-side use-for" in {
      val faultyConfig =
        ConfigFactory
          .parseString(s"""
          akka.javasdk.agent.guardrails {
            "mismatched tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyToolGuard"
              agents = ["some-agent"]
              category = MIXED
              use-for = ["model-response"]
            }
          }
          """)
          .withFallback(config)
      val provider = new GuardrailProvider(system, faultyConfig)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("can only be bound to tool-side use-for")
    }

    "throw from validate when a ModelGuardrail is bound to a tool-side use-for" in {
      val faultyConfig =
        ConfigFactory
          .parseString(s"""
          akka.javasdk.agent.guardrails {
            "mismatched model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyModelGuard"
              agents = ["some-agent"]
              category = MIXED
              use-for = ["mcp-tool-request"]
            }
          }
          """)
          .withFallback(config)
      val provider = new GuardrailProvider(system, faultyConfig)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("can only be bound to model-side use-for")
    }

  }

}
