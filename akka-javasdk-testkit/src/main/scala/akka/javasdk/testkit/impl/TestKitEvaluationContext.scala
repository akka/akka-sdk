/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.annotation.InternalApi
import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.Subject

/**
 * INTERNAL API
 *
 * An [[EvaluationContext]] for use in the [[akka.javasdk.testkit.EvaluatorTestKit]]. Mirrors the judge-session
 * derivation of the production context.
 */
@InternalApi
private[testkit] final class TestKitEvaluationContext(subject: Subject, evaluationId: String)
    extends EvaluationContext {

  override def subject(): Subject = subject

  override def evaluationId(): String = evaluationId

  override def evaluationSession(): String = evaluationSession("default")

  override def evaluationSession(judgeKey: String): String = s"$evaluationId-judge-$judgeKey"
}
