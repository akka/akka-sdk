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
import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.AgentResponseGuardrail
import akka.javasdk.agent.Decision
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.Guardrail.Message
import akka.javasdk.agent.GuardrailContext
import akka.javasdk.agent.MessageContent
import akka.javasdk.agent.ModelCallGuardrail
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
import akka.javasdk.agent.ToolCallGuardrail
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiJsonSchema
import akka.util.ByteString
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.scalatest.OptionValues
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

  class MyToolGuard(context: GuardrailContext) extends ToolCallGuardrail {
    override def decide(ctx: ToolCallGuardrail.CallContext): Decision =
      new Decision.Deny(s"${context.name} says no")
  }

  class AllowingToolGuard extends ToolCallGuardrail {
    override def decide(ctx: ToolCallGuardrail.CallContext): Decision =
      new Decision.Allow()
  }

  // Echoes every context field into the deny reason so a test can assert the full mapping.
  class EchoingToolGuard extends ToolCallGuardrail {
    override def decide(ctx: ToolCallGuardrail.CallContext): Decision =
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

  class MyResponseGuard(context: GuardrailContext) extends AgentResponseGuardrail {
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision =
      new Decision.Deny(s"${context.name} says no")
  }

  // Echoes every context identifier into the deny reason.
  class EchoingModelCallGuard extends ModelCallGuardrail {
    override def decide(ctx: ModelCallGuardrail.CallContext): Decision = {
      val text = ctx.newMessages.asScala.map(textOf).mkString(",")
      new Decision.Deny(s"${ctx.agentId}|${ctx.sessionId}|${ctx.modelName}|$text")
    }
  }

  // Echoes every context identifier into the deny reason.
  class EchoingResponseGuard extends AgentResponseGuardrail {
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision =
      new Decision.Deny(s"${ctx.agentId}|${ctx.sessionId}|${ctx.modelName}|${ctx.reply.text}")
  }

  class MyModelCallGuard extends ModelCallGuardrail {
    override def decide(ctx: ModelCallGuardrail.CallContext): Decision = new Decision.Allow()
  }

  private def textOf(message: Message): String =
    message match {
      case u: Message.UserMessage      => u.contents.asScala.map(textOf).mkString
      case a: Message.AiMessage        => a.text
      case t: Message.ToolCallResponse => t.contents.asScala.map(textOf).mkString
    }

  private def textOf(content: MessageContent): String =
    content match {
      case t: MessageContent.TextMessageContent => t.text
      case _                                    => ""
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

  // One tool round: the user question, the model's tool request, and the tool result.
  private val toolRoundMessages: Seq[SpiAgent.ContextMessage] = Seq(
    new SpiAgent.ContextMessage.UserMessage("first question"),
    new SpiAgent.ContextMessage.AiMessage(
      "calling tool",
      Seq(new SpiAgent.ToolCallRequest("id-1", "search", "{}")),
      None,
      Map.empty),
    new SpiAgent.ContextMessage.ToolCallResponseMessage("id-1", "search", "tool result text"))

  // Holds the per-call context captured by the guard.
  @volatile var capturedModelCallContext: ModelCallGuardrail.CallContext = _

  class CapturingModelCallGuard extends ModelCallGuardrail {
    override def decide(ctx: ModelCallGuardrail.CallContext): Decision = {
      capturedModelCallContext = ctx
      new Decision.Allow()
    }
  }

  // Holds the per-call context captured by the guard.
  @volatile var capturedResponseContext: AgentResponseGuardrail.CallContext = _

  class CapturingResponseGuard extends AgentResponseGuardrail {
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision = {
      capturedResponseContext = ctx
      new Decision.Allow()
    }
  }

  class ToolAndResponseGuard extends ToolCallGuardrail with AgentResponseGuardrail {
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision = new Decision.Allow()
    override def decide(ctx: ToolCallGuardrail.CallContext): Decision = new Decision.Allow()
  }

  class ModelCallAndResponseGuard extends ModelCallGuardrail with AgentResponseGuardrail {
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision = new Decision.Allow()
    override def decide(ctx: ModelCallGuardrail.CallContext): Decision = new Decision.Allow()
  }

  class FailingResponseGuard extends AgentResponseGuardrail {
    val cause = new IllegalStateException("upstream classifier unreachable")
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision =
      new Decision.Fail("could not decide", cause)
  }

  class ThrowingResponseGuard extends AgentResponseGuardrail {
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision =
      throw new IllegalStateException("kaboom")
  }

  class ThrowingToolGuard extends ToolCallGuardrail {
    override def decide(ctx: ToolCallGuardrail.CallContext): Decision =
      throw new IllegalStateException("kaboom")
  }

  // A guard implemented via the sync decide(...) must never have its sync method invoked when the
  // async variant is overridden -- decide throwing here proves the SDK only calls decideAsync.
  abstract class AsyncOnlyResponseGuard extends AgentResponseGuardrail {
    final override def decide(ctx: AgentResponseGuardrail.CallContext): Decision =
      throw new UnsupportedOperationException("sync decide must not be called when decideAsync is overridden")
  }

  // Fails the returned stage rather than throwing or returning Decision.Fail -- the third way a
  // guardrail can fail to reach a verdict.
  class FailedStageResponseGuard extends AsyncOnlyResponseGuard {
    override def decideAsync(ctx: AgentResponseGuardrail.CallContext): CompletionStage[Decision] =
      CompletableFuture.failedFuture(new IllegalStateException("stage blew up"))
  }

  class NullStageResponseGuard extends AsyncOnlyResponseGuard {
    override def decideAsync(ctx: AgentResponseGuardrail.CallContext): CompletionStage[Decision] = null
  }

  class NullDecisionResponseGuard extends AgentResponseGuardrail {
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision = null
  }

  // Completes only when released, so a test can observe that decideAsync(...) doesn't block the caller.
  class SlowResponseGuard extends AsyncOnlyResponseGuard {
    val started = new CountDownLatch(1)
    val release = new CompletableFuture[Decision]()

    override def decideAsync(ctx: AgentResponseGuardrail.CallContext): CompletionStage[Decision] = {
      started.countDown()
      release
    }
  }

  @volatile var slowResponseGuard: SlowResponseGuard = _

  // The provider instantiates guards reflectively, so publish the instance for the test to drive.
  class PublishingSlowResponseGuard extends SlowResponseGuard {
    slowResponseGuard = this
  }

  class WrongGuard
}

class GuardrailProviderSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with OptionValues
    with LogCapturing {
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

    "register a ToolCallGuardrail and attach it at the before-tool-call boundary" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "my tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
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

    "populate the ToolCallGuardrailContext from the tool call content" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "echoing tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
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

    "let a tool call proceed when the before-tool-call ToolCallGuardrail allows it" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "allowing tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AllowingToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
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

    "attach a before-tool-call ToolCallGuardrail to every tool when no tool filter is configured" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "all tools guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AllowingToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("tool-agent", role = None)
      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("tool-a"), toolDescriptor("tool-b")))

      descriptors.map(_.requestGuardrails.size) shouldBe Seq(1, 1)
    }

    "attach a before-tool-call ToolCallGuardrail only to the named tools when a tool filter is configured" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "named tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AllowingToolGuard"
              agents = ["tool-agent"]
              category = TOOL_POLICY
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

    "register a ModelCallGuardrail at before-model-call and expose the newest frame via CallContext" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "echoing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingModelCallGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("model-agent", role = None)
      g.beforeModelCallGuardrails.size shouldBe 1
      g.beforeAgentResponseGuardrails shouldBe empty

      // the newest frame entering this model call is the tool result, not the earlier turns
      val result =
        Await.result(g.beforeModelCallGuardrails.head.evaluate(modelCallContent(toolRoundMessages)), 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "model-agent|session-1|test-model|tool result text"
    }

    "expose the conversation with origins to a ModelCallGuardrail" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelCallGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).beforeModelCallGuardrails.head

      Await.result(spiGuardrail.evaluate(modelCallContent(toolRoundMessages)), 3.seconds).passed shouldBe true

      val conversation = capturedModelCallContext
      conversation.systemMessage() shouldBe "system prompt"

      val received = conversation.messages()
      received.size shouldBe 3

      val userMessage = received.get(0).asInstanceOf[Message.UserMessage]
      userMessage.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "first question"

      val aiMessage = received.get(1).asInstanceOf[Message.AiMessage]
      aiMessage.text() shouldBe "calling tool"
      aiMessage.toolCallRequests().get(0).name() shouldBe "search"

      val toolResult = received.get(2).asInstanceOf[Message.ToolCallResponse]
      toolResult.name() shouldBe "search"
      toolResult.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "tool result text"

      conversation.newMessages().asScala.toSeq shouldBe Seq(toolResult)
    }

    "expose the user message as the new messages on the first model call" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelCallGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).beforeModelCallGuardrails.head

      val messages = Seq(new SpiAgent.ContextMessage.UserMessage("first question"))
      Await.result(spiGuardrail.evaluate(modelCallContent(messages)), 3.seconds).passed shouldBe true

      val conversation = capturedModelCallContext
      val newMessages = conversation.newMessages()
      newMessages.size shouldBe 1
      val userMessage = newMessages.get(0).asInstanceOf[Message.UserMessage]
      userMessage.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "first question"
    }

    "expose each parallel tool result as its own new message at before-model-call" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingModelCallGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).beforeModelCallGuardrails.head

      val messages = Seq(
        new SpiAgent.ContextMessage.UserMessage("weather in Lisbon and Porto?"),
        new SpiAgent.ContextMessage.AiMessage(
          "",
          Seq(
            new SpiAgent.ToolCallRequest("id-1", "weather", """{"city":"Lisbon"}"""),
            new SpiAgent.ToolCallRequest("id-2", "weather", """{"city":"Porto"}""")),
          None,
          Map.empty),
        new SpiAgent.ContextMessage.ToolCallResponseMessage("id-1", "weather", "Lisbon: 22C"),
        new SpiAgent.ContextMessage.ToolCallResponseMessage("id-2", "weather", "Porto: 18C"))

      Await.result(spiGuardrail.evaluate(modelCallContent(messages)), 3.seconds).passed shouldBe true

      val conversation = capturedModelCallContext
      val results = conversation.newMessages().asScala.toSeq.map(_.asInstanceOf[Message.ToolCallResponse])
      results.map(_.id()) shouldBe Seq("id-1", "id-2")
      results.map(r => r.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text()) shouldBe
      Seq("Lisbon: 22C", "Porto: 18C")
    }

    "register an AgentResponseGuardrail at before-agent-response and expose ids via CallContext" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "echoing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
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

    "report a startup error only for a streaming agent with a blocking response guardrail" in {
      GuardrailProvider.streamingResponseGuardrailError("a", streaming = true, Seq("guard")) shouldBe defined
      GuardrailProvider.streamingResponseGuardrailError("a", streaming = false, Seq("guard")) shouldBe empty
      GuardrailProvider.streamingResponseGuardrailError("a", streaming = true, Seq.empty) shouldBe empty
      GuardrailProvider.streamingResponseGuardrailError("a", streaming = false, Seq.empty) shouldBe empty
    }

    "name the agent and every blocking guardrail in the startup error" in {
      val error =
        GuardrailProvider
          .streamingResponseGuardrailError("model-agent", streaming = true, Seq("first guard", "second guard"))
          .value

      error should include("Agent [model-agent]")
      error should include("first guard")
      error should include("second guard")
      error should include("Agent.Effect")
      error should include("report-only")
    }

    "name a blocking response guardrail bound through the agent wildcard" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "wildcard agent guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["*"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      provider.agentGuardrails("any-agent", role = None).blockingResponseGuardrailLabels shouldBe
      Seq("category [MODEL_POLICY], name [wildcard agent guard]")
    }

    "name a blocking response guardrail bound through an agent role" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "role guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agent-roles = ["streaming"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      provider.agentGuardrails("any-agent", role = Some("streaming")).blockingResponseGuardrailLabels shouldBe
      Seq("category [MODEL_POLICY], name [role guard]")
    }

    "name a blocking text guardrail declared with the use-for wildcard" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "wildcard use-for guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["model-agent"]
              category = TOXIC
              use-for = ["*"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      provider.agentGuardrails("model-agent", role = None).blockingResponseGuardrailLabels shouldBe
      Seq("category [TOXIC], name [wildcard use-for guard]")
    }

    "name the blocking guardrails of both response boundaries" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "blocking agent response guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }
            "report-only agent response guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              report-only = true
            }
            "blocking legacy guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["model-agent"]
              category = TOXIC
              use-for = ["model-response"]
            }
            "blocking request guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["model-agent"]
              category = TOXIC
              use-for = ["model-request"]
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("model-agent", role = None)

      g.blockingResponseGuardrailLabels should contain theSameElementsAs Seq(
        "category [MODEL_POLICY], name [blocking agent response guard]",
        "category [TOXIC], name [blocking legacy guard]")
    }

    "name no blocking guardrail when every response guardrail is report-only" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "report-only agent response guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              report-only = true
            }
            "report-only legacy guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["model-agent"]
              category = TOXIC
              use-for = ["model-response"]
              report-only = true
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      provider.agentGuardrails("model-agent", role = None).blockingResponseGuardrailLabels shouldBe empty
    }

    "register an AgentResponseGuardrail and produce a working SpiAgent.Guardrail" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "my model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
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

    "fail the evaluation of a non-text agent reply" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
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

      val error = intercept[IllegalArgumentException](Await.result(spiGuardrail.evaluate(imageReply), 3.seconds))
      error.getMessage should include("Only a text agent response is supported")
    }

    "expose a text agent reply without tool requests" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "capturing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CapturingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).beforeAgentResponseGuardrails.head

      val textContent = agentResponseContent("just text")
      Await.result(spiGuardrail.evaluate(textContent), 3.seconds).passed shouldBe true

      val ctx = capturedResponseContext
      ctx.reply().text() shouldBe "just text"
      ctx.reply().toolCallRequests() shouldBe empty
    }

    "translate a Decision.Fail into a failed Future preserving reason and cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "failing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$FailingResponseGuard"
              agents = ["failing-agent"]
              category = MODEL_POLICY
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

    "translate a failed CompletionStage from an AgentResponseGuardrail into a failed Future preserving the cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "failed stage model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$FailedStageResponseGuard"
              agents = ["failed-stage-agent"]
              category = MODEL_POLICY
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

    "translate a null CompletionStage from an AgentResponseGuardrail into a failed Future" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "null stage model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$NullStageResponseGuard"
              agents = ["null-stage-agent"]
              category = MODEL_POLICY
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

    "translate a null Decision from a sync AgentResponseGuardrail into a failed Future" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "null decision model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$NullDecisionResponseGuard"
              agents = ["null-decision-agent"]
              category = MODEL_POLICY
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

    "not block the caller while an AgentResponseGuardrail's decision is still pending" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "slow model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$PublishingSlowResponseGuard"
              agents = ["slow-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("slow-agent", role = None).beforeAgentResponseGuardrails.head

      val eventual = spiGuardrail.evaluate(agentResponseContent("anything"))

      // evaluate(...) returned while the guard's decision is still outstanding
      slowResponseGuard.started.await(3, TimeUnit.SECONDS) shouldBe true
      eventual.isCompleted shouldBe false

      slowResponseGuard.release.complete(new Decision.Deny("took its time"))

      val result = Await.result(eventual, 3.seconds)
      result.passed shouldBe false
      result.explanation shouldBe "took its time"
    }

    "translate a thrown exception from an AgentResponseGuardrail into a failed Future preserving the throwable as cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "throwing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$ThrowingResponseGuard"
              agents = ["throwing-agent"]
              category = MODEL_POLICY
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

    "translate a thrown exception from a ToolCallGuardrail into a failed Future preserving the throwable as cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "throwing tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$ThrowingToolGuard"
              agents = ["throwing-tool-agent"]
              category = TOOL_POLICY
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

    "throw from validate when a class implements both ToolCallGuardrail and AgentResponseGuardrail" in {
      val faultyConfig =
        ConfigFactory
          .parseString(s"""
          akka.javasdk.agent.guardrails {
            "both guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$ToolAndResponseGuard"
              agents = ["some-agent"]
              category = MIXED
            }
          }
          """)
          .withFallback(config)
      val provider = new GuardrailProvider(system, faultyConfig, testTracerFactory)
      val message = intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage
      message should include(classOf[ToolCallGuardrail].getName)
      message should include(classOf[AgentResponseGuardrail].getName)
    }

    "throw from validate when a class implements both ModelCallGuardrail and AgentResponseGuardrail" in {
      val faultyConfig =
        ConfigFactory
          .parseString(s"""
          akka.javasdk.agent.guardrails {
            "both guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$ModelCallAndResponseGuard"
              agents = ["some-agent"]
              category = MIXED
            }
          }
          """)
          .withFallback(config)
      val provider = new GuardrailProvider(system, faultyConfig, testTracerFactory)
      val message = intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage
      message should include(classOf[ModelCallGuardrail].getName)
      message should include(classOf[AgentResponseGuardrail].getName)
    }

    Seq("MyToolGuard", "MyModelCallGuard", "MyResponseGuard").foreach { guardClass =>
      s"throw from validate when $guardClass defines use-for" in {
        val faultyConfig =
          ConfigFactory
            .parseString(s"""
            akka.javasdk.agent.guardrails {
              "guard with use-for" {
                class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$$guardClass"
                agents = ["some-agent"]
                category = MIXED
                use-for = ["*"]
              }
            }
            """)
            .withFallback(config)
        val provider = new GuardrailProvider(system, faultyConfig, testTracerFactory)
        intercept[IllegalArgumentException] {
          provider.validate()
        }.getMessage should include("must not define use-for")
      }
    }

    "reject a use-for value that names a boundary of the new guardrails" in {
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
      }.getMessage should include("Unknown use-for [before-agent-response]")
    }

    "throw from validate when a TextGuardrail defines no use-for" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "unbound text guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["model-agent"]
              category = TOXIC
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("must define use-for")
    }

    "bind the new guardrails by type and expand the TextGuardrail wildcard" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "wildcard text guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["typed-agent"]
              category = TOXIC
              use-for = ["*"]
            }
            "model call guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyModelCallGuard"
              agents = ["typed-agent"]
              category = MODEL_POLICY
            }
            "response guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyResponseGuard"
              agents = ["typed-agent"]
              category = MODEL_POLICY
            }
            "tool guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AllowingToolGuard"
              agents = ["typed-agent"]
              category = PERMISSION
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("typed-agent", role = None)

      g.modelRequestGuardrails.map(_.name) shouldBe Seq("wildcard text guard")
      g.modelResponseGuardrails.map(_.name) shouldBe Seq("wildcard text guard")
      g.mcpToolRequestGuardrails.map(_.name) shouldBe Seq("wildcard text guard")
      g.mcpToolResponseGuardrails.map(_.name) shouldBe Seq("wildcard text guard")

      g.beforeModelCallGuardrails.map(_.name) shouldBe Seq("model call guard")
      g.beforeAgentResponseGuardrails.map(_.name) shouldBe Seq("response guard")

      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("some-tool")))
      descriptors.head.requestGuardrails.map(_.name) shouldBe Seq("tool guard")
    }

  }

}
