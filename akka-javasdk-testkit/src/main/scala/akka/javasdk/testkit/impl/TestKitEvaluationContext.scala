/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.annotation.InternalApi
import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.Subject
import com.typesafe.config.Config

/**
 * INTERNAL API
 *
 * An [[EvaluationContext]] for use in the [[akka.javasdk.testkit.EvaluatorTestKit]].
 */
@InternalApi
private[testkit] final class TestKitEvaluationContext(subject: Subject, evaluationId: String, evaluationConfig: Config)
    extends EvaluationContext {

  override def subject(): Subject = subject

  override def evaluationId(): String = evaluationId

  override def config(): Config = evaluationConfig
}
