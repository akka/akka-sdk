/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.annotation.InternalApi
import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.ExperimentContext
import akka.javasdk.evaluation.Interaction
import akka.javasdk.evaluation.Subject
import akka.javasdk.impl.evaluation.InteractionRecordAdapter
import akka.javasdk.ledger.LedgerClient

/**
 * INTERNAL API
 *
 * An [[EvaluationContext]] for use in the [[akka.javasdk.testkit.EvaluatorTestKit]]. Resolves [[interaction]] through
 * the given ledger client, the same way the runtime does.
 */
@InternalApi
private[testkit] final class TestKitEvaluationContext(
    subject: Subject,
    evaluationId: String,
    ledgerClient: LedgerClient)
    extends EvaluationContext {

  override def subject(): Subject = subject

  override def evaluationId(): String = evaluationId

  override def interaction(): java.util.Optional[Interaction] =
    subject match {
      case i: Subject.Interaction =>
        java.util.Optional.of(new InteractionRecordAdapter(ledgerClient.getInteraction(i.interactionId())))
      case _ => java.util.Optional.empty()
    }

  // the testkit has no way to seed a triggering experiment yet, so evaluations it runs are never
  // treated as belonging to one
  override def experiment(): java.util.Optional[ExperimentContext] = java.util.Optional.empty()
}
