/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.evaluation.Subject
import akka.runtime.sdk.spi.SpiEvaluator

/**
 * INTERNAL API
 *
 * `Subject` <-> `SpiEvaluator.Subject` conversions, shared by [[EvaluatorImpl]] and [[WorkflowEvaluatorImpl]] so the
 * five `Subject` variants are enumerated in exactly one place.
 */
@InternalApi
private[evaluation] object SubjectConversions {

  def toSdkSubject(subject: SpiEvaluator.Subject): Subject =
    subject match {
      case i: SpiEvaluator.Interaction =>
        new Subject.Interaction(i.interactionId, i.agentComponentId.toJava, i.flowId.toJava)
      case f: SpiEvaluator.Flow =>
        new Subject.Flow(f.flowId)
      case s: SpiEvaluator.Session =>
        new Subject.Session(s.sessionId)
      case e: SpiEvaluator.EvaluatedEvaluation =>
        new Subject.EvaluatedEvaluation(e.evaluationId)
      case x: SpiEvaluator.Experiment =>
        new Subject.Experiment(x.experimentId)
    }

  def toSpiSubject(subject: Subject): SpiEvaluator.Subject =
    subject match {
      case i: Subject.Interaction =>
        new SpiEvaluator.Interaction(i.interactionId(), i.agentComponentId().toScala, i.flowId().toScala)
      case f: Subject.Flow =>
        new SpiEvaluator.Flow(f.flowId())
      case s: Subject.Session =>
        new SpiEvaluator.Session(s.sessionId())
      case e: Subject.EvaluatedEvaluation =>
        new SpiEvaluator.EvaluatedEvaluation(e.evaluationId())
      case x: Subject.Experiment =>
        new SpiEvaluator.Experiment(x.experimentId())
    }
}
