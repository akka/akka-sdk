/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.ledger

import java.time.Instant
import java.util.Optional

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
