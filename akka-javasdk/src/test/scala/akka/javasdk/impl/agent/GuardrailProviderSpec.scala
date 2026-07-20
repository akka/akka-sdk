/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

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
import akka.javasdk.agent.ModelGuardrail.CallContext.ConversationMessage
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

  class MyToolGuard(context: GuardrailContext) extends ToolGuardrail {
    override def decide(ctx: ToolGuardrail.CallContext): Decision =
      new Decision.Deny(s"${context.name} says no")
  }

  class AllowingToolGuard extends ToolGuardrail {
    override def decide(ctx: ToolGuardrail.CallContext): Decision =
      new Decision.Allow()
  }

  // Echoes every context field into the deny reason so a test can assert the full mapping.
  class EchoingToolGuard extends ToolGuardrail {
    override def decide(ctx: ToolGuardrail.CallContext): Decision =
      new Decision.Deny(s"${ctx.agentId}|${ctx.toolName}|${ctx.toolCallId}|${ctx.arguments}|${ctx.sessionId}")
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
    override def decide(ctx: ModelGuardrail.CallContext): Decision =
      new Decision.Deny(s"${context.name} says no")
  }

  // Echoes every context identifier into the deny reason so a test can assert the full mapping.
  class EchoingModelGuard extends ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): Decision =
      new Decision.Deny(s"${ctx.agentId}|${ctx.sessionId}|${ctx.modelName}|${ctx.text}")
  }

  private def agentResponseContent(text: String): SpiAgent.Guardrail.AgentResponseContent =
    new SpiAgent.Guardrail.AgentResponseContent(
      content = new SpiAgent.TextMessageContent(text),
      agentId = "model-agent",
      sessionId = "session-1",
      modelName = "test-model",
      telemetryContext = Context.root())

  private def modelCallContent(messages: Seq[SpiAgent.ContextMessage]): SpiAgent.Guardrail.ModelCallContent =
    new SpiAgent.Guardrail.ModelCallContent(
      systemMessage = "system prompt",
      messages = messages,
      agentId = "model-agent",
      sessionId = "session-1",
      modelName = "test-model",
      telemetryContext = Context.root())

  // Records the per-call context so a test can assert what the model guard received. The provider
  // instantiates the guard via reflection, so the captured context is published through this holder.
  @volatile var capturedModelContext: ModelGuardrail.CallContext = _

  class CapturingModelGuard extends ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): Decision = {
      capturedModelContext = ctx
      new Decision.Allow()
    }
  }

  class BothGuard extends ToolGuardrail with ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): Decision = new Decision.Allow()
    override def decide(ctx: ToolGuardrail.CallContext): Decision = new Decision.Allow()
  }

  class FailingModelGuard extends ModelGuardrail {
    val cause = new IllegalStateException("upstream classifier unreachable")
    override def decide(ctx: ModelGuardrail.CallContext): Decision =
      new Decision.Fail("could not decide", cause)
  }

  class ThrowingModelGuard extends ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): Decision =
      throw new IllegalStateException("kaboom")
  }

  class ThrowingToolGuard extends ToolGuardrail {
    override def decide(ctx: ToolGuardrail.CallContext): Decision =
      throw new IllegalStateException("kaboom")
  }

  // A guard implemented via the sync decide(...) must never have its sync method invoked when the
  // async variant is overridden -- decide throwing here proves the SDK only calls decideAsync.
  abstract class AsyncOnlyModelGuard extends ModelGuardrail {
    final override def decide(ctx: ModelGuardrail.CallContext): Decision =
      throw new UnsupportedOperationException("sync decide must not be called when decideAsync is overridden")
  }

  // Fails the returned stage rather than throwing or returning Decision.Fail -- the third way a
  // guardrail can fail to reach a verdict.
  class FailedStageModelGuard extends AsyncOnlyModelGuard {
    override def decideAsync(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] =
      CompletableFuture.failedFuture(new IllegalStateException("stage blew up"))
  }

  class NullStageModelGuard extends AsyncOnlyModelGuard {
    override def decideAsync(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] = null
  }

  class NullDecisionModelGuard extends ModelGuardrail {
    override def decide(ctx: ModelGuardrail.CallContext): Decision = null
  }

  // Completes only when released, so a test can observe that decideAsync(...) doesn't block the caller.
  class SlowModelGuard extends AsyncOnlyModelGuard {
    val started = new CountDownLatch(1)
    val release = new CompletableFuture[Decision]()

    override def decideAsync(ctx: ModelGuardrail.CallContext): CompletionStage[Decision] = {
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

    "register a ModelGuardrail at before-model-call and expose the newest frame via CallContext" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "echoing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["before-model-call"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("model-agent", role = None)
      g.beforeModelCallGuardrails.size shouldBe 1
      g.beforeAgentResponseGuardrails shouldBe empty

      // the newest frame entering this model call is the tool result, not the earlier turns
      val messages = Seq(
        new SpiAgent.ContextMessage.UserMessage("first question"),
        new SpiAgent.ContextMessage.AiMessage(
          "calling tool",
          Seq(new SpiAgent.ToolCallRequest("id-1", "search", "{}")),
          None,
          Map.empty),
        new SpiAgent.ContextMessage.ToolCallResponseMessage("id-1", "search", "tool result text"))

      val result =
        Await.result(g.beforeModelCallGuardrails.head.evaluate(modelCallContent(messages)), 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "model-agent|session-1|test-model|tool result text"
    }

    "expose the conversation with origins to a ModelGuardrail at before-model-call" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["before-model-call"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).beforeModelCallGuardrails.head

      val messages = Seq(
        new SpiAgent.ContextMessage.UserMessage("first question"),
        new SpiAgent.ContextMessage.AiMessage(
          "calling tool",
          Seq(new SpiAgent.ToolCallRequest("id-1", "search", "{}")),
          None,
          Map.empty),
        new SpiAgent.ContextMessage.ToolCallResponseMessage("id-1", "search", "tool result text"))

      Await.result(spiGuardrail.evaluate(modelCallContent(messages)), 3.seconds).passed shouldBe true

      val ctx = capturedModelContext
      ctx.boundary() shouldBe ModelGuardrail.CallContext.Boundary.BEFORE_MODEL_CALL
      ctx.isBeforeModelCall() shouldBe true

      val conversation = ctx.conversation().get()
      conversation.systemMessage() shouldBe "system prompt"

      val received = conversation.messages()
      received.size shouldBe 3

      val userMessage = received.get(0).asInstanceOf[ConversationMessage.UserMessage]
      userMessage.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "first question"

      val aiMessage = received.get(1).asInstanceOf[ConversationMessage.AiMessage]
      aiMessage.text() shouldBe "calling tool"
      aiMessage.toolRequests().get(0).name() shouldBe "search"

      val toolResult = received.get(2).asInstanceOf[ConversationMessage.ToolCallResult]
      toolResult.toolName() shouldBe "search"
      toolResult.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "tool result text"

      // contents() stays the newest frame; the conversation is only in conversation().messages()
      ctx.textOnly() shouldBe true
      ctx.text() shouldBe "tool result text"
    }

    "register a ModelGuardrail at before-agent-response and expose ids via CallContext" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "echoing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("model-agent", role = None)
      g.beforeAgentResponseGuardrails.size shouldBe 1
      g.modelResponseGuardrails shouldBe empty

      val result =
        Await.result(g.beforeAgentResponseGuardrails.head.evaluate(agentResponseContent("final reply")), 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "model-agent|session-1|test-model|final reply"
    }

    "register a ModelGuardrail and produce a working SpiAgent.Guardrail" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "my model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("model-agent", role = None)
      g.beforeAgentResponseGuardrails.size shouldBe 1
      g.mcpToolRequestGuardrails shouldBe empty

      val spiGuardrail = g.beforeAgentResponseGuardrails.head
      spiGuardrail.name shouldBe "my model guard"
      spiGuardrail.category shouldBe "MODEL_POLICY"

      val result =
        Await.result(spiGuardrail.evaluate(agentResponseContent("anything")), 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "my model guard says no"
    }

    "expose a non-text agent reply to a ModelGuardrail via CallContext.contents() with empty text()" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).beforeAgentResponseGuardrails.head

      val imageReply = new SpiAgent.Guardrail.AgentResponseContent(
        new SpiAgent.ImageBytesMessageContent(ByteString("imgbytes"), "image/png", SpiAgent.ImageMessageContent.Auto),
        "model-agent",
        "session-1",
        "test-model",
        Context.root())

      Await.result(spiGuardrail.evaluate(imageReply), 3.seconds).passed shouldBe true

      val ctx = capturedModelContext
      // text() is empty for a non-text reply; the part is carried in contents() instead
      ctx.textOnly() shouldBe false
      ctx.text() shouldBe ""

      ctx.contents().size shouldBe 1
      val image = ctx.contents().get(0).asInstanceOf[MessageContent.ImageDataMessageContent]
      image.data() shouldBe "imgbytes".getBytes
      image.mimeType() shouldBe java.util.Optional.of("image/png")
    }

    "expose a text-only agent reply as textOnly with the text in text() and contents()" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).beforeAgentResponseGuardrails.head

      val textContent = agentResponseContent("just text")
      Await.result(spiGuardrail.evaluate(textContent), 3.seconds).passed shouldBe true

      val ctx = capturedModelContext
      ctx.boundary() shouldBe ModelGuardrail.CallContext.Boundary.BEFORE_AGENT_RESPONSE
      ctx.isBeforeAgentResponse() shouldBe true
      ctx.textOnly() shouldBe true
      ctx.text() shouldBe "just text"
      ctx.contents().size shouldBe 1
      ctx.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "just text"
      // the before-agent-response boundary carries no conversation
      ctx.conversation().isPresent shouldBe false
    }

    "translate a Decision.Fail into a failed Future preserving reason and cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "failing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$FailingModelGuard"
              agents = ["failing-agent"]
              category = MODEL_POLICY
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("failing-agent", role = None)
      val spiGuardrail = g.beforeAgentResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(agentResponseContent("anything")), 3.seconds)
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
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("failed-stage-agent", role = None).beforeAgentResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(agentResponseContent("anything")), 3.seconds)
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
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("null-stage-agent", role = None).beforeAgentResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(agentResponseContent("anything")), 3.seconds)
      }
      failure.getCause shouldBe a[NullPointerException]
    }

    "translate a null Decision from a sync ModelGuardrail into a failed Future" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "null decision model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$NullDecisionModelGuard"
              agents = ["null-decision-agent"]
              category = MODEL_POLICY
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("null-decision-agent", role = None).beforeAgentResponseGuardrails.head

      val failure = intercept[NullPointerException] {
        Await.result(spiGuardrail.evaluate(agentResponseContent("anything")), 3.seconds)
      }
      failure.getMessage should include("null Decision")
    }

    "not block the caller while a ModelGuardrail's decision is still pending" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "slow model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$PublishingSlowModelGuard"
              agents = ["slow-agent"]
              category = MODEL_POLICY
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("slow-agent", role = None).beforeAgentResponseGuardrails.head

      val eventual = spiGuardrail.evaluate(agentResponseContent("anything"))

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
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("throwing-agent", role = None)
      val spiGuardrail = g.beforeAgentResponseGuardrails.head

      val failure = intercept[RuntimeException] {
        Await.result(spiGuardrail.evaluate(agentResponseContent("anything")), 3.seconds)
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
      }.getMessage should include("can only be bound to model-side use-for values")
    }

    "reject a ModelGuardrail bound to a deprecated model-side use-for value" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "misbound model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyModelGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              use-for = ["model-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("can only be bound to model-side use-for values")
    }

    "reject a TextGuardrail bound to before-agent-response" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "misbound text guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["model-agent"]
              category = TOXIC
              use-for = ["before-agent-response"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("can only be bound to the deprecated use-for values")
    }

    "expand the use-for wildcard per guardrail interface type" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "wildcard text guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["wildcard-agent"]
              category = TOXIC
              use-for = ["*"]
            }
            "wildcard model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyModelGuard"
              agents = ["wildcard-agent"]
              category = MODEL_POLICY
              use-for = ["*"]
            }
            "wildcard tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AllowingToolGuard"
              agents = ["wildcard-agent"]
              category = PERMISSION
              use-for = ["*"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("wildcard-agent", role = None)

      // TextGuardrail "*": the four legacy boundaries, exactly as before this change
      g.modelRequestGuardrails.map(_.name) should contain("wildcard text guard")
      g.modelResponseGuardrails.map(_.name) should contain("wildcard text guard")
      g.mcpToolRequestGuardrails.map(_.name) should contain("wildcard text guard")
      g.mcpToolResponseGuardrails.map(_.name) should contain("wildcard text guard")

      // ModelGuardrail "*": the model-side boundaries only
      g.beforeModelCallGuardrails.map(_.name) shouldBe Seq("wildcard model guard")
      g.beforeAgentResponseGuardrails.map(_.name) shouldBe Seq("wildcard model guard")
      g.modelRequestGuardrails.map(_.name) should not contain "wildcard model guard"
      g.modelResponseGuardrails.map(_.name) should not contain "wildcard model guard"

      // ToolGuardrail "*": before-tool-call only
      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("some-tool")))
      descriptors.head.requestGuardrails.map(_.name) shouldBe Seq("wildcard tool guard")
      g.beforeAgentResponseGuardrails.map(_.name) should not contain "wildcard tool guard"
    }

  }

}
