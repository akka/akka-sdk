/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import java.util.Optional

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.evaluation.Evaluation
import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.Evaluator
import akka.javasdk.evaluation.Interaction
import akka.javasdk.evaluation.Subject
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl.AsyncEffect
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl.CompleteEffect
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl.InconclusiveEffect
import akka.javasdk.ledger.LedgerClient
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
   * The SDK [[EvaluationContext]] backed by the SPI [[SpiEvaluator.EvaluationContext]]. Resolves [[interaction]] on
   * demand through the ledger client, memoized: an evaluator that reads it more than once triggers only the one fetch.
   */
  final class EvaluationContextImpl(spiContext: SpiEvaluator.EvaluationContext, ledgerClient: LedgerClient)
      extends EvaluationContext {

    private lazy val resolvedInteraction: Optional[Interaction] =
      subject() match {
        case i: Subject.Interaction =>
          Optional.of(new InteractionRecordAdapter(ledgerClient.getInteraction(i.interactionId())))
        case _ => Optional.empty()
      }

    override def subject(): Subject = toSdkSubject(spiContext.subject)

    override def evaluationId(): String = spiContext.evaluationId

    override def interaction(): Optional[Interaction] = resolvedInteraction
  }

  private def toSdkSubject(spiSubject: SpiEvaluator.Subject): Subject =
    spiSubject match {
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

  /** The `@Evaluates`-declared subject kind (a `Subject` variant class) as its SPI counterpart. */
  def toSpiSubjectKind(subjectClass: Class[_ <: Subject]): SpiEvaluator.SubjectKind =
    subjectClass match {
      case c if c == classOf[Subject.Interaction]         => SpiEvaluator.SubjectKind.Interaction
      case c if c == classOf[Subject.Flow]                => SpiEvaluator.SubjectKind.Flow
      case c if c == classOf[Subject.Session]             => SpiEvaluator.SubjectKind.Session
      case c if c == classOf[Subject.EvaluatedEvaluation] => SpiEvaluator.SubjectKind.EvaluatedEvaluation
      case c if c == classOf[Subject.Experiment]          => SpiEvaluator.SubjectKind.Experiment
      case other =>
        throw new IllegalArgumentException(s"Unknown Subject kind [${other.getName}] declared in @Evaluates")
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
    sdkExecutionContext: ExecutionContext,
    ledgerClient: LedgerClient)
    extends SpiEvaluator {
  import EvaluatorImpl._

  private val log: Logger = LoggerFactory.getLogger(evaluatorClass)
  private implicit val executionContext: ExecutionContext = sdkExecutionContext

  override def evaluate(spiContext: SpiEvaluator.EvaluationContext): Future[SpiEvaluator.Effect] = {
    val context = new EvaluationContextImpl(spiContext, ledgerClient)
    try {
      val evaluator = factory()
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
      case CompleteEffect(evaluations) =>
        Future.successful(new SpiEvaluator.CompleteEffect(evaluations.map(toSpiEvaluation)))
      case InconclusiveEffect(reason) =>
        Future.successful(new SpiEvaluator.InconclusiveEffect(reason))
      case AsyncEffect(futureEffect) =>
        // pending future is a suspended evaluation; a failed future is recorded as a failure by the runtime
        futureEffect.flatMap(toSpiEffect)
      case unknown =>
        throw new IllegalArgumentException(s"Unknown Evaluator.Effect type ${unknown.getClass}")
    }
}
