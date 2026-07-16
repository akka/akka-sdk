/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.Decision
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.GuardrailContext
import akka.javasdk.agent.MessageContent
import akka.javasdk.agent.ModelGuardrail
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
import akka.javasdk.agent.ToolGuardrail
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiJsonSchema
import akka.util.ByteString
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object GuardrailProviderSpec {
  private val testTracerFactory: () => Tracer = () => OpenTelemetry.noop().getTracer("test")

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

  private def completed(decision: Decision): CompletionStage[Decision] =
    CompletableFuture.completedFuture(decision)

  class MyToolGuard(context: GuardrailContext) extends ToolGuardrail {
    override def decide(ctx: ToolGuardrail.CallContext): CompletionStage[Decision] =
      completed(new Decision.Deny(s"${context.name} says no"))
  }

  class AllowingToolGuard extends ToolGuardrail {
    override def decide(ctx: ToolGuardrail.CallContext): CompletionStage[Decision] =
      completed(new Decision.Allow())
  }

  // Echoes every context field into the deny reason so a test can assert the full mapping.
  class EchoingToolGuard extends ToolGuardrail {
    override def decide(ctx: ToolGuardrail.CallContext): CompletionStage[Decision] =
      completed(
        new Decision.Deny(s"${ctx.agentId}|${ctx.toolName}|${ctx.toolCallId}|${ctx.arguments}|${ctx.sessionId}"))
  }

  private def emptySchema: SpiJsonSchema.JsonSchemaObject =
    new SpiJsonSchema.JsonSchemaObject(description = None, properties = Map.empty, required = Nil)

  private def toolDescriptor(name: String): SpiAgent.ToolDescriptor =
    new SpiAgent.ToolDescriptor(name, s"$name description", emptySchema, requestGuardrails = Nil)

  private def toolCallContent(toolName: String): SpiAgent.Guardrail.ToolCallContent =
    new SpiAgent.Guardrail.ToolCallContent(
      toolName = toolName,
      toolCallId = "call-1",
      arguments = "{}",
      agentId = "tool-agent",
      sessionId = "session-1",
      telemetryContext = Context.root())

  class MyModelGuard(context: GuardrailContext) extends ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] =
      completed(new Decision.Deny(s"${context.name} says no"))
  }

  // Records the per-call context so a test can assert what the model guard received. The provider
  // instantiates the guard via reflection, so the captured context is published through this holder.
  @volatile var capturedModelContext: ModelGuardrail.CallContext = _

  class CapturingModelGuard extends ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] = {
      capturedModelContext = ctx
      completed(new Decision.Allow())
    }
  }

  class BothGuard extends ToolGuardrail with ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] = completed(new Decision.Allow())
    override def decide(ctx: ToolGuardrail.CallContext): CompletionStage[Decision] = completed(new Decision.Allow())
  }

  class FailingModelGuard extends ModelGuardrail {
    val cause = new IllegalStateException("upstream classifier unreachable")
    override def decide(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] =
      completed(new Decision.Fail("could not decide", cause))
  }

  class ThrowingModelGuard extends ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] =
      throw new IllegalStateException("kaboom")
  }

  class ThrowingToolGuard extends ToolGuardrail {
    override def decide(ctx: ToolGuardrail.CallContext): CompletionStage[Decision] =
      throw new IllegalStateException("kaboom")
  }

  // Fails the returned stage rather than throwing or returning Decision.Fail -- the third way a
  // guardrail can fail to reach a verdict.
  class FailedStageModelGuard extends ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] =
      CompletableFuture.failedFuture(new IllegalStateException("stage blew up"))
  }

  class NullStageModelGuard extends ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] = null
  }

  // Completes only when released, so a test can observe that decide(...) doesn't block the caller.
  class SlowModelGuard extends ModelGuardrail {
    val started = new CountDownLatch(1)
    val release = new CompletableFuture[Decision]()

    override def decide(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] = {
      started.countDown()
      release
    }
  }

  @volatile var slowModelGuard: SlowModelGuard = _

  // The provider instantiates guards reflectively, so publish the instance for the test to drive.
  class PublishingSlowModelGuard extends SlowModelGuard {
    slowModelGuard = this
  }

  class WrongGuard
}

class GuardrailProviderSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with LogCapturing {
  import GuardrailProviderSpec._

  "The GuardrailProvider" should {
    "validate" in {
      val provider = new GuardrailProvider(system, config, testTracerFactory)
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
      val provider = new GuardrailProvider(system, faultyConfig, testTracerFactory)
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
      val provider = new GuardrailProvider(system, faultyConfig, testTracerFactory)
      intercept[ConfigException] {
        provider.validate()
      }.getMessage should include("threshold has type STRING rather than NUMBER")
    }

    "select guardrails for an agent" in {
      val provider = new GuardrailProvider(system, config, testTracerFactory)

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
      val provider = new GuardrailProvider(system, wildcardConfig, testTracerFactory)

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

    "register a ToolGuardrail and attach it at the before-tool-call boundary" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "my tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
              use-for = ["before-tool-call"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("tool-agent", role = None)
      // before-tool-call guardrails are not exposed as model/mcp boundaries
      g.modelRequestGuardrails shouldBe empty
      g.mcpToolRequestGuardrails shouldBe empty

      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("some-tool")))
      descriptors.head.requestGuardrails.size shouldBe 1

      val spiGuardrail = descriptors.head.requestGuardrails.head
      spiGuardrail.name shouldBe "my tool guard"
      spiGuardrail.category shouldBe "TOOL_POLICY"

      val result =
        Await.result(spiGuardrail.evaluate(toolCallContent("some-tool")), 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "my tool guard says no"
    }

    "populate the ToolGuardrailContext from the tool call content" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "echoing tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
              use-for = ["before-tool-call"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("tool-agent", role = None)
      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("some-tool")))
      val spiGuardrail = descriptors.head.requestGuardrails.head

      val result = Await.result(spiGuardrail.evaluate(toolCallContent("some-tool")), 3.seconds)
      // matches the fields built by toolCallContent(...): agentId|toolName|toolCallId|arguments|sessionId
      result.explanation shouldBe "tool-agent|some-tool|call-1|{}|session-1"
    }

    "let a tool call proceed when the before-tool-call ToolGuardrail allows it" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "allowing tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AllowingToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
              use-for = ["before-tool-call"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("tool-agent", role = None)
      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("some-tool")))

      val spiGuardrail = descriptors.head.requestGuardrails.head
      val result = Await.result(spiGuardrail.evaluate(toolCallContent("some-tool")), 3.seconds)
      result.passed shouldBe true
    }

    "attach a before-tool-call ToolGuardrail to every tool when no tool filter is configured" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "all tools guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AllowingToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
              use-for = ["before-tool-call"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("tool-agent", role = None)
      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("tool-a"), toolDescriptor("tool-b")))

      descriptors.map(_.requestGuardrails.size) shouldBe Seq(1, 1)
    }

    "attach a before-tool-call ToolGuardrail only to the named tools when a tool filter is configured" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "named tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AllowingToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
              use-for = ["before-tool-call"]
              tools = ["allowed-tool"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("tool-agent", role = None)
      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("allowed-tool"), toolDescriptor("other-tool")))

      val byName = descriptors.map(d => d.name -> d.requestGuardrails.size).toMap
      byName("allowed-tool") shouldBe 1
      // a tool not named by the filter is returned unchanged, without guardrails
      byName("other-tool") shouldBe 0
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

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("model-agent", role = None)
      g.modelResponseGuardrails.size shouldBe 1
      g.mcpToolRequestGuardrails shouldBe empty

      val spiGuardrail = g.modelResponseGuardrails.head
      spiGuardrail.name shouldBe "my model guard"
      spiGuardrail.category shouldBe "MODEL_POLICY"

      val result =
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything", Context.root())), 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "my model guard says no"
    }

    "expose multimodal content to a ModelGuardrail via CallContext.contents()" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["model-request"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).modelRequestGuardrails.head

      val contents = Seq[SpiAgent.MessageContent](
        new SpiAgent.TextMessageContent("describe this"),
        new SpiAgent.ImageBytesMessageContent(ByteString("imgbytes"), "image/png", SpiAgent.ImageMessageContent.Auto),
        new SpiAgent.ImageUriMessageContent(
          URI.create("https://example.com/x.png"),
          SpiAgent.ImageMessageContent.High,
          None))
      val multimodal = new SpiAgent.Guardrail.MultimodalContent(contents, Context.root())

      val result = Await.result(spiGuardrail.evaluate(multimodal), 3.seconds)
      result.passed shouldBe true

      val ctx = capturedModelContext
      // text() is empty for multimodal content; the text prompt is carried in contents() instead
      ctx.textOnly() shouldBe false
      ctx.text() shouldBe ""

      val received = ctx.contents()
      received.size shouldBe 3
      received.get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "describe this"

      val image = received.get(1).asInstanceOf[MessageContent.ImageDataMessageContent]
      image.data() shouldBe "imgbytes".getBytes
      image.mimeType() shouldBe java.util.Optional.of("image/png")

      received.get(2).asInstanceOf[MessageContent.ImageUrlMessageContent].uri() shouldBe URI.create(
        "https://example.com/x.png")
    }

    "preserve multiple text parts as-is in contents() without joining them into text()" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["model-request"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).modelRequestGuardrails.head

      // two text parts on either side of an image part
      val contents = Seq[SpiAgent.MessageContent](
        new SpiAgent.TextMessageContent("first"),
        new SpiAgent.ImageBytesMessageContent(ByteString("imgbytes"), "image/png", SpiAgent.ImageMessageContent.Auto),
        new SpiAgent.TextMessageContent("second"))
      val multimodal = new SpiAgent.Guardrail.MultimodalContent(contents, Context.root())

      Await.result(spiGuardrail.evaluate(multimodal), 3.seconds).passed shouldBe true

      val ctx = capturedModelContext
      // multimodal text() is empty regardless of how many text parts there are: no joining
      ctx.textOnly() shouldBe false
      ctx.text() shouldBe ""

      val received = ctx.contents()
      // the text parts are preserved individually, in order, alongside the image part
      received.size shouldBe 3
      received.get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "first"
      received.get(1) shouldBe a[MessageContent.ImageDataMessageContent]
      received.get(2).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "second"
    }

    "expose an image-only multimodal message with empty text()" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["model-request"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).modelRequestGuardrails.head

      val contents = Seq[SpiAgent.MessageContent](
        new SpiAgent.ImageBytesMessageContent(ByteString("imgbytes"), "image/png", SpiAgent.ImageMessageContent.Auto))
      val multimodal = new SpiAgent.Guardrail.MultimodalContent(contents, Context.root())

      Await.result(spiGuardrail.evaluate(multimodal), 3.seconds).passed shouldBe true

      val ctx = capturedModelContext
      ctx.textOnly() shouldBe false
      ctx.text() shouldBe ""
      ctx.contents().size shouldBe 1
      ctx.contents().get(0) shouldBe a[MessageContent.ImageDataMessageContent]
    }

    "expose a text-only model request as textOnly with the text in text() and contents()" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["model-request"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).modelRequestGuardrails.head

      val textContent = new SpiAgent.Guardrail.TextContent("just text", Context.root())
      Await.result(spiGuardrail.evaluate(textContent), 3.seconds).passed shouldBe true

      val ctx = capturedModelContext
      ctx.textOnly() shouldBe true
      ctx.text() shouldBe "just text"
      ctx.contents().size shouldBe 1
      ctx.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "just text"
    }

    "translate a Decision.Fail into a failed Future preserving reason and cause" in {
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

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("failing-agent", role = None)
      val spiGuardrail = g.modelResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything", Context.root())), 3.seconds)
      }
      failure.getMessage shouldBe "could not decide"
      failure.getCause shouldBe a[IllegalStateException]
      failure.getCause.getMessage shouldBe "upstream classifier unreachable"
    }

    "translate a failed CompletionStage from a ModelGuardrail into a failed Future preserving the cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "failed stage model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$FailedStageModelGuard"
              agents = ["failed-stage-agent"]
              category = MODEL_POLICY
              use-for = ["model-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("failed-stage-agent", role = None).modelResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything", Context.root())), 3.seconds)
      }
      failure.getMessage shouldBe "stage blew up"
      failure.getCause shouldBe a[IllegalStateException]
      failure.getCause.getMessage shouldBe "stage blew up"
    }

    "translate a null CompletionStage from a ModelGuardrail into a failed Future" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "null stage model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$NullStageModelGuard"
              agents = ["null-stage-agent"]
              category = MODEL_POLICY
              use-for = ["model-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("null-stage-agent", role = None).modelResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything", Context.root())), 3.seconds)
      }
      failure.getCause shouldBe a[NullPointerException]
    }

    "not block the caller while a ModelGuardrail's decision is still pending" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "slow model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$PublishingSlowModelGuard"
              agents = ["slow-agent"]
              category = MODEL_POLICY
              use-for = ["model-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("slow-agent", role = None).modelResponseGuardrails.head

      val eventual = spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything", Context.root()))

      // evaluate(...) returned while the guard's decision is still outstanding
      slowModelGuard.started.await(3, TimeUnit.SECONDS) shouldBe true
      eventual.isCompleted shouldBe false

      slowModelGuard.release.complete(new Decision.Deny("took its time"))

      val result = Await.result(eventual, 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "took its time"
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

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("throwing-agent", role = None)
      val spiGuardrail = g.modelResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(new SpiAgent.Guardrail.TextContent("anything", Context.root())), 3.seconds)
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
              use-for = ["before-tool-call"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("throwing-tool-agent", role = None)
      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("some-tool")))
      val spiGuardrail = descriptors.head.requestGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(toolCallContent("some-tool")), 3.seconds)
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
      val provider = new GuardrailProvider(system, faultyConfig, testTracerFactory)
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
      val provider = new GuardrailProvider(system, faultyConfig, testTracerFactory)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("can only be bound to the before-tool-call use-for")
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
      val provider = new GuardrailProvider(system, faultyConfig, testTracerFactory)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("can only be bound to model-side use-for")
    }

  }

}
