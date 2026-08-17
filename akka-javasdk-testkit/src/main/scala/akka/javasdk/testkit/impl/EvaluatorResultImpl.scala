/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.annotation.InternalApi
import akka.javasdk.evaluation.Evaluation
import akka.javasdk.evaluation.Evaluator
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl
import akka.javasdk.testkit.EvaluatorResult

/**
 * INTERNAL API
 */
@InternalApi
private[testkit] object EvaluatorResultImpl {
  private val DefaultTimeout = 10.seconds
}

/**
 * INTERNAL API
 */
@InternalApi
private[testkit] final class EvaluatorResultImpl(effect: EvaluatorEffectImpl.PrimaryEffect) extends EvaluatorResult {
  import EvaluatorResultImpl.DefaultTimeout

  def this(effect: Evaluator.Effect) = this(effect.asInstanceOf[EvaluatorEffectImpl.PrimaryEffect])

  private val async: Boolean = effect.isInstanceOf[EvaluatorEffectImpl.AsyncEffect]

  // resolve any async effects to a terminal complete/inconclusive
  private val terminal: EvaluatorEffectImpl.PrimaryEffect = resolve(effect)

  private def resolve(effect: EvaluatorEffectImpl.PrimaryEffect): EvaluatorEffectImpl.PrimaryEffect =
    effect match {
      case EvaluatorEffectImpl.AsyncEffect(future) =>
        resolve(Await.result(future, DefaultTimeout).asInstanceOf[EvaluatorEffectImpl.PrimaryEffect])
      case other => other
    }

  override def isAsync(): Boolean = async

  override def isComplete(): Boolean = terminal.isInstanceOf[EvaluatorEffectImpl.CompleteEffect]

  override def isInconclusive(): Boolean = terminal.isInstanceOf[EvaluatorEffectImpl.InconclusiveEffect]

  override def getEvaluation(): Evaluation =
    getEffectOfType(classOf[EvaluatorEffectImpl.CompleteEffect]).evaluation

  override def getInconclusiveReason(): String =
    getEffectOfType(classOf[EvaluatorEffectImpl.InconclusiveEffect]).reason

  private def getEffectOfType[E](expectedClass: Class[E]): E = {
    if (expectedClass.isInstance(terminal)) terminal.asInstanceOf[E]
    else
      throw new NoSuchElementException(
        "expected effect type [" + expectedClass.getName + "] but found [" + terminal.getClass.getName + "]")
  }
}
