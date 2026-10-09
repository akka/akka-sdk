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
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.AgentResponseGuardrail
import akka.javasdk.agent.Decision
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.Guardrail.Message
import akka.javasdk.agent.GuardrailContext
import akka.javasdk.agent.MessageContent
import akka.javasdk.agent.ModelCallGuardrail
import akka.javasdk.agent.ModelCallSimilarityGuard
import akka.javasdk.agent.SimilarityGuard
import akka.javasdk.agent.TextGuardrail
import akka.javasdk.agent.ToolCallGuardrail
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiGuardrail
import akka.runtime.sdk.spi.SpiJsonSchema
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
  private val similarityGuardClass = classOf[SimilarityGuard]

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
      new Decision.Allow("tool call looks fine")
  }

  // Echoes every context field into the deny reason so a test can assert the full mapping.
  class EchoingToolGuard extends ToolCallGuardrail {
    override def decide(ctx: ToolCallGuardrail.CallContext): Decision =
      new Decision.Deny(s"${ctx.agentId}|${ctx.toolName}|${ctx.toolCallId}|${ctx.arguments}|${ctx.sessionId}")
  }

  private def emptySchema: SpiJsonSchema.JsonSchemaObject =
    new SpiJsonSchema.JsonSchemaObject(description = None, properties = Map.empty, required = Nil)

  private def toolDescriptor(name: String): SpiAgent.ToolDescriptor =
    new SpiAgent.ToolDescriptor(name, s"$name description", emptySchema, toolCallGuardrails = Nil)

  private def toolCallContext(toolName: String): SpiGuardrail.ToolCallContext =
    new SpiGuardrail.ToolCallContext(
      toolName = toolName,
      toolCallId = "call-1",
      arguments = "{}",
      agentId = "tool-agent",
      sessionId = "session-1",
      telemetryContext = Context.root())

  private def reasonOf(decision: SpiGuardrail.Decision): String =
    decision match {
      case allow: SpiGuardrail.Allow => allow.reason
      case deny: SpiGuardrail.Deny   => deny.reason
      case fail: SpiGuardrail.Fail   => fail.reason
    }

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

  private def agentResponseContext(response: String): SpiGuardrail.AgentResponseContext =
    new SpiGuardrail.AgentResponseContext(
      response = response,
      agentId = "model-agent",
      sessionId = "session-1",
      modelName = "test-model",
      telemetryContext = Context.root())

  private def modelCallContext(messages: Seq[SpiAgent.ContextMessage]): SpiGuardrail.ModelCallContext =
    new SpiGuardrail.ModelCallContext(
      systemMessage = "system prompt",
      messages = messages,
      agentId = "model-agent",
      sessionId = "session-1",
      modelName = "test-model",
      telemetryContext = Context.root())

  private def spiUserMessage(text: String): SpiAgent.ContextMessage =
    new SpiAgent.ContextMessage.UserMessage(Seq(new SpiAgent.TextMessageContent(text)), sanitized = false)

  private def spiToolCallResponse(id: String, name: String, text: String): SpiAgent.ContextMessage =
    new SpiAgent.ContextMessage.ToolCallResponseMessage(
      id,
      name,
      Seq(new SpiAgent.TextMessageContent(text)),
      sanitized = false,
      isError = false)

  // One tool round: the user question, the model's tool request, and the tool result.
  private val toolRoundMessages: Seq[SpiAgent.ContextMessage] = Seq(
    spiUserMessage("first question"),
    new SpiAgent.ContextMessage.AiMessage(
      "calling tool",
      Seq(new SpiAgent.ToolCallRequest("id-1", "search", "{}")),
      None,
      Map.empty),
    spiToolCallResponse("id-1", "search", "tool result text"))

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

  class CauselessFailingResponseGuard extends AgentResponseGuardrail {
    override def decide(ctx: AgentResponseGuardrail.CallContext): Decision =
      new Decision.Fail("could not decide")
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
      g1.entries.head.guardrail.getClass shouldBe similarityGuardClass
      g1.legacyModelRequestGuardrails.size shouldBe 1
      g1.legacyModelRequestGuardrails.head.getClass shouldBe classOf[SpiAgent.SimilarityGuard]
      g1.legacyModelRequestGuardrails.head.asInstanceOf[SpiAgent.SimilarityGuard].category shouldBe "PROMPT_INJECTION"
      g1.legacyModelResponseGuardrails shouldBe empty

      val g2 = provider.agentGuardrails("planner-agent", role = Some("worker"))
      g2.entries.size shouldBe 2
      g2.legacyModelRequestGuardrails.size shouldBe 1
      g2.legacyModelRequestGuardrails.head.getClass shouldBe classOf[SpiAgent.SimilarityGuard]
      g2.legacyModelResponseGuardrails.size shouldBe 1
      g2.legacyModelResponseGuardrails.head.name shouldBe "my guard"
    }

    "report legacy model guardrails only for a TextGuardrail on a model use-for" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "mcp text guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["new-agent"]
              category = TOXIC
              use-for = ["mcp-tool-request"]
            }
            "model call guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyModelCallGuard"
              agents = ["new-agent"]
              category = MODEL_POLICY
            }
            "response guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyResponseGuard"
              agents = ["new-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)
      val provider = new GuardrailProvider(system, cfg, testTracerFactory)

      provider.agentGuardrails("new-agent", role = None).hasLegacyModelGuardrails shouldBe false
      provider.agentGuardrails("planner-agent", role = None).hasLegacyModelGuardrails shouldBe true
      provider.agentGuardrails("other-agent", role = Some("worker")).hasLegacyModelGuardrails shouldBe true
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

    "register a ToolCallGuardrail and attach it to the tool descriptor" in {
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
      // tool-call guardrails are not exposed as legacy model or MCP guardrails
      g.legacyModelRequestGuardrails shouldBe empty
      g.legacyMcpToolRequestGuardrails shouldBe empty

      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("some-tool")))
      descriptors.head.toolCallGuardrails.size shouldBe 1

      val spiGuardrail = descriptors.head.toolCallGuardrails.head
      spiGuardrail.settings.name shouldBe "my tool guard"
      spiGuardrail.settings.category shouldBe "TOOL_POLICY"

      val decision = Await.result(spiGuardrail.decide(toolCallContext("some-tool")), 3.seconds)
      decision shouldBe a[SpiGuardrail.Deny]
      reasonOf(decision) shouldBe "my tool guard says no"
    }

    "populate the ToolCallGuardrail.CallContext from the tool call context" in {
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
      val spiGuardrail = descriptors.head.toolCallGuardrails.head

      val decision = Await.result(spiGuardrail.decide(toolCallContext("some-tool")), 3.seconds)
      // matches the fields built by toolCallContext(...): agentId|toolName|toolCallId|arguments|sessionId
      reasonOf(decision) shouldBe "tool-agent|some-tool|call-1|{}|session-1"
    }

    "give the ToolCallGuardrail an empty tool call id when the context has none" in {
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
      val spiGuardrail = g.withToolGuardrails(Seq(toolDescriptor("some-tool"))).head.toolCallGuardrails.head

      val withoutId = new SpiGuardrail.ToolCallContext(
        toolName = "some-tool",
        toolCallId = null,
        arguments = "{}",
        agentId = "tool-agent",
        sessionId = "session-1",
        telemetryContext = Context.root())

      val decision = Await.result(spiGuardrail.decide(withoutId), 3.seconds)
      reasonOf(decision) shouldBe "tool-agent|some-tool||{}|session-1"
    }

    "let a tool call proceed when the ToolCallGuardrail allows it" in {
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

      val spiGuardrail = descriptors.head.toolCallGuardrails.head
      val decision = Await.result(spiGuardrail.decide(toolCallContext("some-tool")), 3.seconds)
      decision shouldBe a[SpiGuardrail.Allow]
      reasonOf(decision) shouldBe "tool call looks fine"
    }

    "attach a ToolCallGuardrail to every tool when no tool filter is configured" in {
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

      descriptors.map(_.toolCallGuardrails.size) shouldBe Seq(1, 1)
    }

    "attach a ToolCallGuardrail only to the named tools when a tool filter is configured" in {
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

      val byName = descriptors.map(d => d.name -> d.toolCallGuardrails.size).toMap
      byName("allowed-tool") shouldBe 1
      // a tool not named by the filter is returned unchanged, without guardrails
      byName("other-tool") shouldBe 0
    }

    "register a ModelCallGuardrail and expose the newest frame via CallContext" in {
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
      g.guardrails.modelCallGuardrails.size shouldBe 1
      g.guardrails.agentResponseGuardrails shouldBe empty

      // the newest frame entering this model call is the tool result, not the earlier turns
      val decision =
        Await.result(g.guardrails.modelCallGuardrails.head.decide(modelCallContext(toolRoundMessages)), 3.seconds)
      decision shouldBe a[SpiGuardrail.Deny]
      reasonOf(decision) shouldBe "model-agent|session-1|test-model|tool result text"
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
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).guardrails.modelCallGuardrails.head

      Await.result(spiGuardrail.decide(modelCallContext(toolRoundMessages)), 3.seconds) shouldBe a[SpiGuardrail.Allow]

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
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).guardrails.modelCallGuardrails.head

      val messages = Seq(spiUserMessage("first question"))
      Await.result(spiGuardrail.decide(modelCallContext(messages)), 3.seconds) shouldBe a[SpiGuardrail.Allow]

      val conversation = capturedModelCallContext
      val newMessages = conversation.newMessages()
      newMessages.size shouldBe 1
      val userMessage = newMessages.get(0).asInstanceOf[Message.UserMessage]
      userMessage.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text() shouldBe "first question"
    }

    "expose each parallel tool result as its own new message to a ModelCallGuardrail" in {
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
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).guardrails.modelCallGuardrails.head

      val messages = Seq(
        spiUserMessage("weather in Lisbon and Porto?"),
        new SpiAgent.ContextMessage.AiMessage(
          "",
          Seq(
            new SpiAgent.ToolCallRequest("id-1", "weather", """{"city":"Lisbon"}"""),
            new SpiAgent.ToolCallRequest("id-2", "weather", """{"city":"Porto"}""")),
          None,
          Map.empty),
        spiToolCallResponse("id-1", "weather", "Lisbon: 22C"),
        spiToolCallResponse("id-2", "weather", "Porto: 18C"))

      Await.result(spiGuardrail.decide(modelCallContext(messages)), 3.seconds) shouldBe a[SpiGuardrail.Allow]

      val conversation = capturedModelCallContext
      val results = conversation.newMessages().asScala.toSeq.map(_.asInstanceOf[Message.ToolCallResponse])
      results.map(_.id()) shouldBe Seq("id-1", "id-2")
      results.map(r => r.contents().get(0).asInstanceOf[MessageContent.TextMessageContent].text()) shouldBe
      Seq("Lisbon: 22C", "Porto: 18C")
    }

    "report a startup error only for a streaming agent with a blocking response guardrail" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "blocking guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["blocking-agent"]
              category = MODEL_POLICY
            }
            "report-only guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["report-only-agent"]
              category = MODEL_POLICY
              report-only = true
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      def error(agentId: String, streaming: Boolean) =
        provider.agentGuardrails(agentId, role = None).streamingResponseGuardrailError(agentId, streaming)

      error("blocking-agent", streaming = true) shouldBe defined
      error("blocking-agent", streaming = false) shouldBe empty
      error("report-only-agent", streaming = true) shouldBe empty
      error("unguarded-agent", streaming = true) shouldBe empty
    }

    "name the agent and every blocking guardrail in the startup error" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "first guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }
            "second guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val error =
        provider
          .agentGuardrails("model-agent", role = None)
          .streamingResponseGuardrailError("model-agent", isStreaming = true)
          .value

      error should include("Agent [model-agent]")
      error should include("Guardrail [first guard]")
      error should include("Guardrail [second guard]")
      error should include("Agent.Effect")
      error should include("report-only")
    }

    "list the blocking guardrails of the startup error in name order" in {
      val names = Seq("guard e", "guard d", "guard c", "guard b", "guard a")
      val entries = names.map { name =>
        s"""
            "$name" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }"""
      }
      val cfg = ConfigFactory
        .parseString(s"akka.javasdk.agent.guardrails {${entries.mkString}\n}")
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      provider.agentGuardrails("model-agent", role = None).enforcingResponseGuardrailLabels shouldBe
      names.sorted.map(name => s"Guardrail [$name]")
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
      provider.agentGuardrails("any-agent", role = None).enforcingResponseGuardrailLabels shouldBe
      Seq("Guardrail [wildcard agent guard]")
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
      provider.agentGuardrails("any-agent", role = Some("streaming")).enforcingResponseGuardrailLabels shouldBe
      Seq("Guardrail [role guard]")
    }

    "report no startup error for a streaming agent with a blocking legacy model-response guardrail" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "model-response guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["model-agent"]
              category = TOXIC
              use-for = ["model-response"]
            }
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
      val g = provider.agentGuardrails("model-agent", role = None)

      g.legacyModelResponseGuardrails.map(_.name) should contain theSameElementsAs Seq(
        "model-response guard",
        "wildcard use-for guard")
      g.enforcingResponseGuardrailLabels shouldBe empty
      g.streamingResponseGuardrailError("model-agent", isStreaming = true) shouldBe empty
    }

    "name only the blocking agent response guardrails" in {
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
            "blocking mcp tool response guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyGuard"
              agents = ["model-agent"]
              category = TOXIC
              use-for = ["mcp-tool-response"]
            }
            "blocking model call guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyModelCallGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
            }
            "blocking tool call guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$AllowingToolGuard"
              agents = ["model-agent"]
              category = TOOL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("model-agent", role = None)

      g.entries should have size 7
      g.enforcingResponseGuardrailLabels shouldBe Seq("Guardrail [blocking agent response guard]")
    }

    "name no blocking guardrail when every agent response guardrail is report-only" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "report-only agent response guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$EchoingResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              report-only = true
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      provider.agentGuardrails("model-agent", role = None).enforcingResponseGuardrailLabels shouldBe empty
    }

    "register an AgentResponseGuardrail and expose ids via CallContext" in {
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
      g.guardrails.agentResponseGuardrails.size shouldBe 1
      g.legacyModelResponseGuardrails shouldBe empty

      val decision =
        Await.result(g.guardrails.agentResponseGuardrails.head.decide(agentResponseContext("final reply")), 3.seconds)
      decision shouldBe a[SpiGuardrail.Deny]
      reasonOf(decision) shouldBe "model-agent|session-1|test-model|final reply"
    }

    "register an AgentResponseGuardrail with its settings" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "my model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyResponseGuard"
              agents = ["model-agent"]
              category = MODEL_POLICY
              report-only = true
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("model-agent", role = None)
      g.guardrails.agentResponseGuardrails.size shouldBe 1
      g.legacyMcpToolRequestGuardrails shouldBe empty

      val spiGuardrail = g.guardrails.agentResponseGuardrails.head
      spiGuardrail.settings.name shouldBe "my model guard"
      spiGuardrail.settings.category shouldBe "MODEL_POLICY"
      spiGuardrail.settings.reportOnly shouldBe true

      val decision = Await.result(spiGuardrail.decide(agentResponseContext("anything")), 3.seconds)
      decision shouldBe a[SpiGuardrail.Deny]
      reasonOf(decision) shouldBe "my model guard says no"
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
      val spiGuardrail = provider.agentGuardrails("model-agent", role = None).guardrails.agentResponseGuardrails.head

      Await.result(spiGuardrail.decide(agentResponseContext("just text")), 3.seconds) shouldBe a[SpiGuardrail.Allow]

      val ctx = capturedResponseContext
      ctx.reply().text() shouldBe "just text"
      ctx.reply().toolCallRequests() shouldBe empty
    }

    "map a Decision.Fail to a Fail with its reason and cause" in {
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
      val spiGuardrail = provider.agentGuardrails("failing-agent", role = None).guardrails.agentResponseGuardrails.head

      val decision = Await.result(spiGuardrail.decide(agentResponseContext("anything")), 3.seconds)
      decision shouldBe a[SpiGuardrail.Fail]
      val fail = decision.asInstanceOf[SpiGuardrail.Fail]
      fail.reason shouldBe "could not decide"
      fail.cause.map(_.getMessage) shouldBe Some("upstream classifier unreachable")
    }

    "map a Decision.Fail without a cause to a Fail without a cause" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "causeless failing model guard" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$CauselessFailingResponseGuard"
              agents = ["failing-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val spiGuardrail = provider.agentGuardrails("failing-agent", role = None).guardrails.agentResponseGuardrails.head

      val decision = Await.result(spiGuardrail.decide(agentResponseContext("anything")), 3.seconds)
      decision shouldBe a[SpiGuardrail.Fail]
      decision.asInstanceOf[SpiGuardrail.Fail].cause shouldBe None
    }

    "return the failed CompletionStage of an AgentResponseGuardrail as a failed Future" in {
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
      val spiGuardrail =
        provider.agentGuardrails("failed-stage-agent", role = None).guardrails.agentResponseGuardrails.head

      val failure = intercept[IllegalStateException] {
        Await.result(spiGuardrail.decide(agentResponseContext("anything")), 3.seconds)
      }
      failure.getMessage shouldBe "stage blew up"
    }

    "fail the Future when an AgentResponseGuardrail returns a null CompletionStage" in {
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
      val spiGuardrail =
        provider.agentGuardrails("null-stage-agent", role = None).guardrails.agentResponseGuardrails.head

      val failure = intercept[NullPointerException] {
        Await.result(spiGuardrail.decide(agentResponseContext("anything")), 3.seconds)
      }
      failure.getMessage shouldBe "Guardrail returned a null CompletionStage"
    }

    "return a null decision when an AgentResponseGuardrail returns a null Decision" in {
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
      val spiGuardrail =
        provider.agentGuardrails("null-decision-agent", role = None).guardrails.agentResponseGuardrails.head

      Await.result(spiGuardrail.decide(agentResponseContext("anything")), 3.seconds) shouldBe null
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
      val spiGuardrail = provider.agentGuardrails("slow-agent", role = None).guardrails.agentResponseGuardrails.head

      val eventual = spiGuardrail.decide(agentResponseContext("anything"))

      // decide(...) returned while the guard's decision is still outstanding
      slowResponseGuard.started.await(3, TimeUnit.SECONDS) shouldBe true
      eventual.isCompleted shouldBe false

      slowResponseGuard.release.complete(new Decision.Deny("took its time"))

      val decision = Await.result(eventual, 3.seconds)
      decision shouldBe a[SpiGuardrail.Deny]
      reasonOf(decision) shouldBe "took its time"
    }

    "throw the exception thrown by an AgentResponseGuardrail" in {
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
      val spiGuardrail = provider.agentGuardrails("throwing-agent", role = None).guardrails.agentResponseGuardrails.head

      val failure = intercept[IllegalStateException] {
        spiGuardrail.decide(agentResponseContext("anything"))
      }
      failure.getMessage shouldBe "kaboom"
    }

    "throw the exception thrown by a ToolCallGuardrail" in {
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
      val spiGuardrail = descriptors.head.toolCallGuardrails.head

      val failure = intercept[IllegalStateException] {
        spiGuardrail.decide(toolCallContext("some-tool"))
      }
      failure.getMessage shouldBe "kaboom"
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

      g.legacyModelRequestGuardrails.map(_.name) shouldBe Seq("wildcard text guard")
      g.legacyModelResponseGuardrails.map(_.name) shouldBe Seq("wildcard text guard")
      g.legacyMcpToolRequestGuardrails.map(_.name) shouldBe Seq("wildcard text guard")
      g.legacyMcpToolResponseGuardrails.map(_.name) shouldBe Seq("wildcard text guard")

      g.guardrails.modelCallGuardrails.map(_.settings.name) shouldBe Seq("model call guard")
      g.guardrails.agentResponseGuardrails.map(_.settings.name) shouldBe Seq("response guard")

      val descriptors = g.withToolGuardrails(Seq(toolDescriptor("some-tool")))
      descriptors.head.toolCallGuardrails.map(_.settings.name) shouldBe Seq("tool guard")
    }

    "hand each guardrail to the runtime with its control id and the agents it applies to" in {
      val cfg = ConfigFactory
        .parseString("""
          akka.javasdk.agent.guardrails."my guard".control-id = "AI-GR-01"
        """)
        .withFallback(config)
      val provider = new GuardrailProvider(system, cfg, testTracerFactory)

      val entries = provider.spiGuardrails {
        case "my guard" => Set("worker-agent")
        case _          => Set.empty
      }

      entries.map(g => g.name -> (g.controlId, g.enabledForComponents)).toMap shouldBe Map(
        "request prompt injection" -> (None, Set.empty),
        "my guard" -> (Some("AI-GR-01"), Set("worker-agent")))
      val myGuard = entries.find(_.name == "my guard").get
      myGuard.implementationClass shouldBe classOf[MyGuard].getName
      myGuard.reportOnly shouldBe true
      myGuard.config.getString("control-id") shouldBe "AI-GR-01"
    }

    "bind a ModelCallSimilarityGuard as a model-call SpiGuardrail.SimilarityGuard" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "model call jailbreak" {
              class = "akka.javasdk.agent.ModelCallSimilarityGuard"
              agents = ["similarity-agent"]
              category = JAILBREAK
              report-only = true
              threshold = 0.8
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val g = provider.agentGuardrails("similarity-agent", role = None)

      g.entries.head.guardrail.getClass shouldBe classOf[ModelCallSimilarityGuard]
      g.legacyModelRequestGuardrails shouldBe empty
      g.guardrails.modelCallGuardrails.size shouldBe 1

      val spiGuard = g.guardrails.modelCallGuardrails.head.asInstanceOf[SpiGuardrail.SimilarityGuard]
      spiGuard.settings.name shouldBe "model call jailbreak"
      spiGuard.settings.category shouldBe "JAILBREAK"
      spiGuard.settings.reportOnly shouldBe true
      spiGuard.datasetResourceDir shouldBe "guardrail/jailbreak"
      spiGuard.threshold shouldBe 0.8
    }

    "bind a ModelCallSimilarityGuard and a custom ModelCallGuardrail on the same agent" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "model call jailbreak" {
              class = "akka.javasdk.agent.ModelCallSimilarityGuard"
              agents = ["similarity-agent"]
              category = JAILBREAK
              threshold = 0.8
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
            "custom model call" {
              class = "akka.javasdk.impl.agent.GuardrailProviderSpec$$MyModelCallGuard"
              agents = ["similarity-agent"]
              category = MODEL_POLICY
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      val bySettingsName =
        provider
          .agentGuardrails("similarity-agent", role = None)
          .guardrails
          .modelCallGuardrails
          .map { g =>
            g.settings.name -> g.getClass
          }
          .toMap

      bySettingsName shouldBe Map(
        "model call jailbreak" -> classOf[SpiGuardrail.SimilarityGuard],
        "custom model call" -> classOf[GuardrailProvider.ModelCallGuardrailAdapter])
    }

    Seq("0", "-0.1", "1.5", "\"NaN\"").foreach { threshold =>
      s"throw from validate when a ModelCallSimilarityGuard has threshold $threshold" in {
        val cfg = ConfigFactory.parseString(s"""
            akka.javasdk.agent.guardrails {
              "model call jailbreak" {
                class = "akka.javasdk.agent.ModelCallSimilarityGuard"
                agents = ["similarity-agent"]
                category = JAILBREAK
                threshold = $threshold
                bad-examples-resource-dir = "guardrail/jailbreak"
              }
            }
          """)

        val provider = new GuardrailProvider(system, cfg, testTracerFactory)
        intercept[IllegalArgumentException] {
          provider.validate()
        }.getMessage should include("Guardrail [model call jailbreak] threshold must be greater than 0 and at most 1")
      }
    }

    "accept a ModelCallSimilarityGuard with threshold 1" in {
      val cfg = ConfigFactory.parseString(s"""
          akka.javasdk.agent.guardrails {
            "model call jailbreak" {
              class = "akka.javasdk.agent.ModelCallSimilarityGuard"
              agents = ["similarity-agent"]
              category = JAILBREAK
              threshold = 1
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
          }
        """)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      provider.validate()

      val spiGuard = provider
        .agentGuardrails("similarity-agent", role = None)
        .guardrails
        .modelCallGuardrails
        .head
        .asInstanceOf[SpiGuardrail.SimilarityGuard]
      spiGuard.threshold shouldBe 1.0
    }

    "throw from validate when a ModelCallSimilarityGuard has a bad-examples-resource-dir that does not exist" in {
      val cfg = ConfigFactory.parseString(s"""
          akka.javasdk.agent.guardrails {
            "model call jailbreak" {
              class = "akka.javasdk.agent.ModelCallSimilarityGuard"
              agents = ["similarity-agent"]
              category = JAILBREAK
              threshold = 0.8
              bad-examples-resource-dir = "guardrail/no-such-dir"
            }
          }
        """)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage shouldBe
      "Guardrail [model call jailbreak] bad-examples-resource-dir [guardrail/no-such-dir] not found on the classpath"
    }

    "throw from validate when a ModelCallSimilarityGuard defines use-for" in {
      val cfg = ConfigFactory
        .parseString(s"""
          akka.javasdk.agent.guardrails {
            "model call jailbreak" {
              class = "akka.javasdk.agent.ModelCallSimilarityGuard"
              agents = ["similarity-agent"]
              category = JAILBREAK
              use-for = ["model-request"]
              threshold = 0.8
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
          }
        """)
        .withFallback(config)

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include("must not define use-for")
    }

    "warn that a SimilarityGuard on model-request is replaced by ModelCallSimilarityGuard" in {
      val cfg = ConfigFactory.parseString(s"""
          akka.javasdk.agent.guardrails {
            "legacy jailbreak" {
              class = "akka.javasdk.agent.SimilarityGuard"
              agents = ["legacy-agent"]
              category = JAILBREAK
              use-for = ["model-request"]
              threshold = 0.75
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
          }
        """)

      LoggingTestKit
        .warn(
          "Guardrail [legacy jailbreak] uses akka.javasdk.agent.SimilarityGuard for [model-request]. " +
          "This usage is deprecated. SimilarityGuard will support only mcp-tool-request and mcp-tool-response. " +
          "For model-request, use akka.javasdk.agent.ModelCallSimilarityGuard. It does not take a use-for setting. " +
          "Then remove the agents and agent roles from [legacy jailbreak].")
        .expect {
          LoggingTestKit.warn("Implement akka.javasdk.agent.ModelCallGuardrail").withOccurrences(0).expect {
            new GuardrailProvider(system, cfg, testTracerFactory).validate()
          }
        }
    }

    "warn that a SimilarityGuard on use-for [*] keeps only the MCP uses" in {
      val cfg = ConfigFactory.parseString(s"""
          akka.javasdk.agent.guardrails {
            "legacy jailbreak" {
              class = "akka.javasdk.agent.SimilarityGuard"
              agents = ["legacy-agent"]
              category = JAILBREAK
              use-for = ["*"]
              threshold = 0.75
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
          }
        """)

      LoggingTestKit
        .warn(
          "Guardrail [legacy jailbreak] uses akka.javasdk.agent.SimilarityGuard for [model-request, model-response]. " +
          "This usage is deprecated. SimilarityGuard will support only mcp-tool-request and mcp-tool-response. " +
          "For model-request, use akka.javasdk.agent.ModelCallSimilarityGuard. It does not take a use-for setting. " +
          "The SDK has no replacement for model-response. " +
          "Then set use-for on [legacy jailbreak] to [mcp-tool-request, mcp-tool-response].")
        .expect {
          LoggingTestKit.warn("Implement akka.javasdk.agent.ModelCallGuardrail").withOccurrences(0).expect {
            new GuardrailProvider(system, cfg, testTracerFactory).validate()
          }
        }
    }

    "warn that a SimilarityGuard on model-request and mcp-tool-request keeps only the MCP use" in {
      val cfg = ConfigFactory.parseString(s"""
          akka.javasdk.agent.guardrails {
            "legacy jailbreak" {
              class = "akka.javasdk.agent.SimilarityGuard"
              agents = ["legacy-agent"]
              category = JAILBREAK
              use-for = ["model-request", "mcp-tool-request"]
              threshold = 0.75
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
          }
        """)

      LoggingTestKit
        .warn(
          "Guardrail [legacy jailbreak] uses akka.javasdk.agent.SimilarityGuard for [model-request]. " +
          "This usage is deprecated. SimilarityGuard will support only mcp-tool-request and mcp-tool-response. " +
          "For model-request, use akka.javasdk.agent.ModelCallSimilarityGuard. It does not take a use-for setting. " +
          "Then set use-for on [legacy jailbreak] to [mcp-tool-request].")
        .expect {
          new GuardrailProvider(system, cfg, testTracerFactory).validate()
        }
    }

    "warn that the built-in \"default jailbreak\" is replaced by \"default model-call jailbreak\"" in {
      val cfg = ConfigFactory
        .parseString("""akka.javasdk.agent.guardrails."default jailbreak".agents = ["legacy-agent"]""")
        .withFallback(ConfigFactory.defaultReference())

      LoggingTestKit
        .warn(
          "Guardrail [default jailbreak] uses akka.javasdk.agent.SimilarityGuard for [model-request]. " +
          "This usage is deprecated. SimilarityGuard will support only mcp-tool-request and mcp-tool-response. " +
          "For model-request, enable \"default model-call jailbreak\" for the same agents and agent roles. " +
          "Then remove the agents and agent roles from [default jailbreak].")
        .expect {
          LoggingTestKit.warn("To keep your current settings").withOccurrences(0).expect {
            new GuardrailProvider(system, cfg, testTracerFactory).validate()
          }
        }
    }

    "warn with the settings to copy when the built-in \"default jailbreak\" has overrides" in {
      val cfg = ConfigFactory
        .parseString("""
          akka.javasdk.agent.guardrails."default jailbreak" {
            agents = ["legacy-agent"]
            report-only = true
            threshold = 0.9
            bad-examples-resource-dir = "my/own"
          }
        """)
        .withFallback(ConfigFactory.defaultReference())

      LoggingTestKit
        .warn(
          "For model-request, enable \"default model-call jailbreak\" for the same agents and agent roles. " +
          "To keep your current settings, set threshold = 0.9, report-only = true, " +
          "bad-examples-resource-dir = \"my/own\" on \"default model-call jailbreak\". " +
          "Then remove the agents and agent roles from [default jailbreak].")
        .expect {
          new GuardrailProvider(system, cfg, testTracerFactory).validate()
        }
    }

    "warn once for an entry that applies to several agents and roles" in {
      val cfg = ConfigFactory.parseString(s"""
          akka.javasdk.agent.guardrails {
            "legacy jailbreak" {
              class = "akka.javasdk.agent.SimilarityGuard"
              agents = ["agent-a", "agent-b"]
              agent-roles = ["worker"]
              category = JAILBREAK
              use-for = ["model-request"]
              threshold = 0.75
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
          }
        """)

      LoggingTestKit.warn("Guardrail [legacy jailbreak] uses akka.javasdk.agent.SimilarityGuard").expect {
        new GuardrailProvider(system, cfg, testTracerFactory).validate()
      }
    }

    "point to \"default model-call jailbreak\" when \"default jailbreak\" uses ModelCallSimilarityGuard" in {
      val cfg = ConfigFactory
        .parseString("""
          akka.javasdk.agent.guardrails."default jailbreak" {
            class = "akka.javasdk.agent.ModelCallSimilarityGuard"
            agents = ["legacy-agent"]
          }
        """)
        .withFallback(ConfigFactory.defaultReference())

      val provider = new GuardrailProvider(system, cfg, testTracerFactory)
      intercept[IllegalArgumentException] {
        provider.validate()
      }.getMessage should include(
        "The use-for of [default jailbreak] comes from reference.conf. To use " +
        "akka.javasdk.agent.ModelCallSimilarityGuard, enable \"default model-call jailbreak\" instead.")
    }

    "warn once when a SimilarityGuard uses model-request and model-response" in {
      val cfg = ConfigFactory.parseString(s"""
          akka.javasdk.agent.guardrails {
            "legacy jailbreak" {
              class = "akka.javasdk.agent.SimilarityGuard"
              agents = ["legacy-agent"]
              category = JAILBREAK
              use-for = ["model-request", "model-response"]
              threshold = 0.75
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
          }
        """)

      LoggingTestKit
        .warn(
          "Guardrail [legacy jailbreak] uses akka.javasdk.agent.SimilarityGuard for [model-request, model-response].")
        .expect {
          LoggingTestKit.warn("uses deprecated use-for value(s)").withOccurrences(0).expect {
            new GuardrailProvider(system, cfg, testTracerFactory).validate()
          }
        }
    }

    "not warn about a SimilarityGuard on mcp-tool-request" in {
      val cfg = ConfigFactory.parseString(s"""
          akka.javasdk.agent.guardrails {
            "mcp jailbreak" {
              class = "akka.javasdk.agent.SimilarityGuard"
              agents = ["mcp-agent"]
              category = JAILBREAK
              use-for = ["mcp-tool-request"]
              threshold = 0.75
              bad-examples-resource-dir = "guardrail/jailbreak"
            }
          }
        """)

      LoggingTestKit.warn("deprecated").withOccurrences(0).expect {
        val provider = new GuardrailProvider(system, cfg, testTracerFactory)
        provider.validate()
        provider.agentGuardrails("mcp-agent", role = None).legacyMcpToolRequestGuardrails.size shouldBe 1
      }
    }

    "warn and keep both when an agent has the deprecated and the model-call similarity guards" in {
      val cfg = ConfigFactory
        .parseString("""
          akka.javasdk.agent.guardrails."default jailbreak".agents = ["jailbreak-agent"]
          akka.javasdk.agent.guardrails."default model-call jailbreak".agents = ["jailbreak-agent"]
        """)
        .withFallback(ConfigFactory.defaultReference())
      val provider = new GuardrailProvider(system, cfg, testTracerFactory)

      val g = LoggingTestKit
        .warn(
          "Agent [jailbreak-agent] has both the deprecated SimilarityGuard [default jailbreak] with use-for " +
          "[model-request] and the ModelCallSimilarityGuard [default model-call jailbreak]. Both check the user " +
          "message and the tool results. Remove the agents and agent roles from [default jailbreak].")
        .expect {
          provider.agentGuardrails("jailbreak-agent", role = None)
        }

      g.legacyModelRequestGuardrails.map(_.name) shouldBe Seq("default jailbreak")
      g.guardrails.modelCallGuardrails.map(_.settings.name) shouldBe Seq("default model-call jailbreak")
    }

    "warn and keep both when the model-call similarity guard comes from agent-roles" in {
      val cfg = ConfigFactory
        .parseString("""
          akka.javasdk.agent.guardrails."default jailbreak".agents = ["jailbreak-agent"]
          akka.javasdk.agent.guardrails."default model-call jailbreak".agent-roles = ["*"]
        """)
        .withFallback(ConfigFactory.defaultReference())
      val provider = new GuardrailProvider(system, cfg, testTracerFactory)

      val g = LoggingTestKit
        .warn(
          "Agent [jailbreak-agent] has both the deprecated SimilarityGuard [default jailbreak] with use-for " +
          "[model-request] and the ModelCallSimilarityGuard [default model-call jailbreak]. Both check the user " +
          "message and the tool results. Remove the agents and agent roles from [default jailbreak].")
        .expect {
          provider.agentGuardrails("jailbreak-agent", role = Some("worker"))
        }

      g.legacyModelRequestGuardrails.map(_.name) shouldBe Seq("default jailbreak")
      g.guardrails.modelCallGuardrails.map(_.settings.name) shouldBe Seq("default model-call jailbreak")
    }

    "not warn about both similarity guards when they are on different agents" in {
      val cfg = ConfigFactory
        .parseString("""
          akka.javasdk.agent.guardrails."default jailbreak".agents = ["legacy-agent"]
          akka.javasdk.agent.guardrails."default model-call jailbreak".agents = ["model-call-agent"]
        """)
        .withFallback(ConfigFactory.defaultReference())
      val provider = new GuardrailProvider(system, cfg, testTracerFactory)

      LoggingTestKit.warn("has both the deprecated SimilarityGuard").withOccurrences(0).expect {
        provider.agentGuardrails("legacy-agent", role = None)
        provider.agentGuardrails("model-call-agent", role = None)
      }
    }

    "bind the built-in \"default model-call jailbreak\" as a model-call guardrail without a deprecation warning" in {
      val cfg = ConfigFactory
        .parseString("""akka.javasdk.agent.guardrails."default model-call jailbreak".agents = ["jailbreak-agent"]""")
        .withFallback(ConfigFactory.defaultReference())

      LoggingTestKit.warn("deprecated").withOccurrences(0).expect {
        val provider = new GuardrailProvider(system, cfg, testTracerFactory)
        provider.validate()

        val g = provider.agentGuardrails("jailbreak-agent", role = None)
        g.legacyModelRequestGuardrails shouldBe empty
        val spiGuard = g.guardrails.modelCallGuardrails.head.asInstanceOf[SpiGuardrail.SimilarityGuard]
        spiGuard.settings.name shouldBe "default model-call jailbreak"
        spiGuard.datasetResourceDir shouldBe "guardrail/jailbreak"
        spiGuard.threshold shouldBe 0.75
      }
    }

  }

}
