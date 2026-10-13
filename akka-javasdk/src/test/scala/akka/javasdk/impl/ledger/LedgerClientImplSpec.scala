/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.ledger

import java.time.Instant
import java.util.Optional

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
}
