/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.evaluation.Evaluation
import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.Evaluator
import akka.javasdk.evaluation.Subject
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl.AsyncEffect
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl.ErrorEffect
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl.RecordEffect
import akka.runtime.sdk.spi.SpiEvaluator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object EvaluatorImpl {

  /**
   * INTERNAL API
   *
   * The SDK [[EvaluationContext]] backed by the SPI [[SpiEvaluator.EvaluationContext]].
   */
  final class EvaluationContextImpl(spiContext: SpiEvaluator.EvaluationContext) extends EvaluationContext {

    override def subject(): Subject = toSdkSubject(spiContext.subject)

    override def evaluationId(): String = spiContext.evaluationId

    override def evaluationSession(): String = evaluationSession("default")

    override def evaluationSession(judgeKey: String): String =
      s"${spiContext.evaluationId}-judge-$judgeKey"
  }

  private def toSdkSubject(spiSubject: SpiEvaluator.Subject): Subject =
    spiSubject match {
      case flow: SpiEvaluator.FlowInteraction =>
        new Subject.FlowInteraction(
          flow.flowId,
          flow.agentComponentId,
          flow.agentInstanceId.toJava,
          flow.sessionId,
          flow.sequenceNr)
      case agent: SpiEvaluator.AgentInteraction =>
        new Subject.AgentInteraction(agent.agentComponentId, agent.sessionId, agent.writerId, agent.sequenceNr)
    }

  private def toSpiEvaluation(evaluation: Evaluation): SpiEvaluator.Evaluation =
    new SpiEvaluator.Evaluation(
      passed = evaluation.passed(),
      explanation = evaluation.explanation(),
      score = evaluation.score().toScala.map(_.doubleValue()),
      label = evaluation.label().toScala,
      attributes = evaluation.attributes().asScala.toMap)
}

/**
 * INTERNAL API
 *
 * Adapts a user [[Evaluator]] to the [[SpiEvaluator]] expected by the runtime. A new instance is created per evaluation
 * by the descriptor's instance factory.
 */
@InternalApi
private[impl] final class EvaluatorImpl[E <: Evaluator](
    factory: () => E,
    evaluatorClass: Class[E],
    sdkExecutionContext: ExecutionContext)
    extends SpiEvaluator {
  import EvaluatorImpl._

  private val log: Logger = LoggerFactory.getLogger(evaluatorClass)
  private implicit val executionContext: ExecutionContext = sdkExecutionContext

  override def evaluate(spiContext: SpiEvaluator.EvaluationContext): Future[SpiEvaluator.Effect] = {
    val context = new EvaluationContextImpl(spiContext)
    try {
      val evaluator = factory()
      val effect = evaluator.evaluate(context)
      toSpiEffect(effect)
    } catch {
      // a thrown error is a failure (distinct from a deliberate error() effect); the runtime records it as such
      case NonFatal(ex) =>
        log.error(s"Failure during evaluation in Evaluator component [${evaluatorClass.getSimpleName}].", ex)
        Future.failed(ex)
    }
  }

  private def toSpiEffect(effect: Evaluator.Effect): Future[SpiEvaluator.Effect] =
    effect match {
      case RecordEffect(evaluations) =>
        Future.successful(new SpiEvaluator.RecordEffect(evaluations.map(toSpiEvaluation)))
      case ErrorEffect(reason) =>
        Future.successful(new SpiEvaluator.ErrorEffect(new SpiEvaluator.Error(reason, None)))
      case AsyncEffect(futureEffect) =>
        // pending future is a suspended evaluation; a failed future is recorded as a failure by the runtime
        futureEffect.flatMap(toSpiEffect)
      case unknown =>
        throw new IllegalArgumentException(s"Unknown Evaluator.Effect type ${unknown.getClass}")
    }
}
