/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.ledger

import java.time.Duration
import java.time.Instant
import java.util.Optional

import akka.javasdk.ledger.GuardrailDecisionRecord
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiLedger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LedgerClientImplSpec extends AnyWordSpec with Matchers {

  private def spiEvaluationRecord(controlId: Option[String]): SpiLedger.EvaluationRecord =
    new SpiLedger.EvaluationRecord(
      evaluationId = "evaluation-1",
      evaluatorComponentId = "quality-evaluator",
      controlId = controlId,
      trigger = SpiLedger.EvaluationTrigger.OnInteraction,
      subject = new SpiLedger.InteractionSubject("interaction-1", "support-agent"),
      outcome = new SpiLedger.EvaluationInconclusive("no verdict"),
      timestamp = Instant.EPOCH)

  private def spiInteractionRecord(modelResponses: SpiLedger.ModelResponse*): SpiLedger.InteractionRecord =
    new SpiLedger.InteractionRecord(
      interactionId = "interaction-1",
      sessionId = "session-1",
      agentComponentId = "support-agent",
      flowId = None,
      metadata = new SpiLedger.InteractionMetadata(
        new SpiLedger.ModelConfig("anthropic", "claude", "", -1.0, -1.0, -1, -1),
        Map.empty,
        Instant.EPOCH,
        Instant.EPOCH,
        SpiLedger.FinishReason.Stop),
      systemMessage = "",
      userMessage = Seq.empty,
      modelResponses = modelResponses,
      toolCallResponses = Seq.empty,
      taskContext = None,
      failure = None,
      timestamp = Instant.EPOCH)

  private def spiGuardrailDecisionRecord(
      controlId: Option[String],
      toolCallId: Option[String],
      toolName: Option[String],
      toolOrigin: SpiLedger.ToolOrigin,
      mcpEndpoint: Option[String]): SpiLedger.GuardrailDecisionRecord =
    new SpiLedger.GuardrailDecisionRecord(
      recordId = "guardrail-1",
      agentComponentId = "support-agent",
      interactionId = "interaction-1",
      sessionId = "session-1",
      flowId = Some("flow-1"),
      name = "no-refunds",
      controlId = controlId,
      category = "policy",
      boundary = SpiLedger.GuardrailBoundary.BeforeToolCall,
      decision = SpiLedger.GuardrailDecision.Deny,
      reportOnly = true,
      explanation = "refunds are not allowed",
      duration = Duration.ofMillis(12),
      toolCallId = toolCallId,
      toolName = toolName,
      toolOrigin = toolOrigin,
      mcpEndpoint = mcpEndpoint,
      timestamp = Instant.EPOCH)

  private def spiModelResponse(tokenUsage: SpiAgent.SpiTokenUsage): SpiLedger.ModelResponse =
    new SpiLedger.ModelResponse("m1", "", tokenUsage, "", Seq.empty)

  "Mapping an interaction record" should {

    "carry the token counts of each model response" in {
      val record = LedgerClientImpl.toInteractionRecord(
        spiInteractionRecord(
          spiModelResponse(new SpiAgent.SpiTokenUsage(100, 10, 11, 22, 133)),
          spiModelResponse(new SpiAgent.SpiTokenUsage(50, 5))))

      val tokenUsage = record.modelResponses().get(0).tokenUsage()
      tokenUsage.inputTokens() shouldEqual 100
      tokenUsage.outputTokens() shouldEqual 10
      tokenUsage.cacheReadInputTokens() shouldEqual 11
      tokenUsage.cacheWriteInputTokens() shouldEqual 22
      tokenUsage.totalInputTokens() shouldEqual 133

      record.totalInputTokens() shouldEqual 183
      record.totalOutputTokens() shouldEqual 15
    }
  }

  "Mapping an evaluation record" should {

    "carry the control id of the evaluator" in {
      val record = LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(Some("EVAL-1")))

      record.controlId() shouldEqual Optional.of("EVAL-1")
    }

    "carry an empty control id when the evaluator declares none" in {
      val record = LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(None))

      record.controlId() shouldEqual Optional.empty()
    }
  }

  "Mapping a guardrail decision record" should {

    "carry the decision and the inspected tool" in {
      val record = LedgerClientImpl.toGuardrailDecisionRecord(
        spiGuardrailDecisionRecord(
          controlId = Some("GR-1"),
          toolCallId = Some("call-1"),
          toolName = Some("RefundTools_refund"),
          toolOrigin = SpiLedger.ToolOrigin.RemoteMcp,
          mcpEndpoint = Some("https://mcp.example.com")))

      record.recordId() shouldEqual "guardrail-1"
      record.agentComponentId() shouldEqual "support-agent"
      record.interactionId() shouldEqual "interaction-1"
      record.sessionId() shouldEqual "session-1"
      record.flowId() shouldEqual Optional.of("flow-1")
      record.name() shouldEqual "no-refunds"
      record.controlId() shouldEqual Optional.of("GR-1")
      record.category() shouldEqual "policy"
      record.boundary() shouldEqual GuardrailDecisionRecord.Boundary.BEFORE_TOOL_CALL
      record.decision() shouldEqual GuardrailDecisionRecord.Decision.DENY
      record.reportOnly() shouldBe true
      record.explanation() shouldEqual "refunds are not allowed"
      record.duration() shouldEqual Duration.ofMillis(12)
      record.toolCallId() shouldEqual Optional.of("call-1")
      record.toolName() shouldEqual Optional.of("RefundTools_refund")
      record.toolOrigin() shouldEqual GuardrailDecisionRecord.ToolOrigin.REMOTE_MCP
      record.mcpEndpoint() shouldEqual Optional.of("https://mcp.example.com")
      record.timestamp() shouldEqual Instant.EPOCH
    }

    "carry empty optionals when the guardrail inspected no tool and declares no control id" in {
      val record = LedgerClientImpl.toGuardrailDecisionRecord(
        spiGuardrailDecisionRecord(
          controlId = None,
          toolCallId = None,
          toolName = None,
          toolOrigin = SpiLedger.ToolOrigin.Unspecified,
          mcpEndpoint = None))

      record.controlId() shouldEqual Optional.empty()
      record.toolCallId() shouldEqual Optional.empty()
      record.toolName() shouldEqual Optional.empty()
      record.toolOrigin() shouldEqual GuardrailDecisionRecord.ToolOrigin.UNSPECIFIED
      record.mcpEndpoint() shouldEqual Optional.empty()
    }
  }
}
