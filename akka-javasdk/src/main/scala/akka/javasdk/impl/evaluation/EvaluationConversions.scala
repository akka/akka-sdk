/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.evaluation.Evaluation
import akka.javasdk.evaluation.Subject
import akka.runtime.sdk.spi.SpiEvaluator

/**
 * INTERNAL API
 *
 * Conversions between the SDK evaluation types and the SPI types.
 */
@InternalApi
private[javasdk] object EvaluationConversions {

  def toSdkSubject(subject: SpiEvaluator.Subject): Subject =
    subject match {
      case interaction: SpiEvaluator.Interaction =>
        new Subject.Interaction(interaction.interactionId, interaction.agentComponentId, interaction.flowId.toJava)
      case workflow: SpiEvaluator.Workflow =>
        new Subject.Workflow(workflow.workflowId, workflow.workflowComponentId)
    }

  def toSpiSubject(subject: Subject): SpiEvaluator.Subject =
    subject match {
      case interaction: Subject.Interaction =>
        new SpiEvaluator.Interaction(
          interaction.interactionId(),
          interaction.agentComponentId(),
          interaction.flowId().toScala)
      case workflow: Subject.Workflow =>
        new SpiEvaluator.Workflow(workflow.workflowId(), workflow.workflowComponentId())
    }

  def toSpiEvaluation(evaluation: Evaluation): SpiEvaluator.Evaluation =
    new SpiEvaluator.Evaluation(
      passed = evaluation.passed(),
      explanation = evaluation.explanation(),
      score = evaluation.score().toScala.map(_.doubleValue()),
      label = evaluation.label().toScala,
      attributes = evaluation.attributes().asScala.toMap)
}
