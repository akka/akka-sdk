/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.Evaluator
import akka.javasdk.evaluation.Subject
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl.CompleteEffect
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl.InconclusiveEffect
import akka.runtime.sdk.spi.SpiEvaluator
import io.opentelemetry.context.{ Context => OtelContext }
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

    override def subject(): Subject = EvaluationConversions.toSdkSubject(spiContext.subject)

    override def evaluationId(): String = spiContext.evaluationId
  }

}

/**
 * INTERNAL API
 *
 * Adapts a user [[Evaluator]] to the [[SpiEvaluator]] expected by the runtime. A new instance is created per evaluation
 * by the descriptor's instance factory.
 */
@InternalApi
private[impl] final class EvaluatorImpl[E <: Evaluator](factory: Option[OtelContext] => E, evaluatorClass: Class[E])
    extends SpiEvaluator {
  import EvaluatorImpl._

  private val log: Logger = LoggerFactory.getLogger(evaluatorClass)

  override def evaluate(spiContext: SpiEvaluator.EvaluationContext): Future[SpiEvaluator.Effect] = {
    val context = new EvaluationContextImpl(spiContext)
    try {
      val evaluator = factory(Option(spiContext.telemetryContext))
      val effect = evaluator.evaluate(context)
      toSpiEffect(effect)
    } catch {
      // a thrown exception is a failure (distinct from the deliberate inconclusive() outcome); the
      // runtime catches the failed future, logs the throwable, and records a failed evaluation
      case NonFatal(ex) =>
        log.error(s"Failure during evaluation in Evaluator component [${evaluatorClass.getSimpleName}].", ex)
        Future.failed(ex)
    }
  }

  private def toSpiEffect(effect: Evaluator.Effect): Future[SpiEvaluator.Effect] =
    effect match {
      case CompleteEffect(evaluation) =>
        Future.successful(new SpiEvaluator.CompleteEffect(EvaluationConversions.toSpiEvaluation(evaluation)))
      case InconclusiveEffect(reason) =>
        Future.successful(new SpiEvaluator.InconclusiveEffect(reason))
      case unknown =>
        throw new IllegalArgumentException(s"Unknown Evaluator.Effect type ${unknown.getClass}")
    }
}
