/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.evaluation.Evaluation
import akka.javasdk.evaluation.Evaluator
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl
import akka.javasdk.testkit.EvaluatorResult

/**
 * INTERNAL API
 */
@InternalApi
private[testkit] final class EvaluatorResultImpl(effect: EvaluatorEffectImpl.PrimaryEffect) extends EvaluatorResult {
  def this(effect: Evaluator.Effect) = this(effect.asInstanceOf[EvaluatorEffectImpl.PrimaryEffect])

  override def isComplete(): Boolean = effect.isInstanceOf[EvaluatorEffectImpl.CompleteEffect]

  override def isInconclusive(): Boolean = effect.isInstanceOf[EvaluatorEffectImpl.InconclusiveEffect]

  override def getEvaluations(): java.util.List[Evaluation] =
    getEffectOfType(classOf[EvaluatorEffectImpl.CompleteEffect]).evaluations.asJava

  override def getInconclusiveReason(): String =
    getEffectOfType(classOf[EvaluatorEffectImpl.InconclusiveEffect]).reason

  private def getEffectOfType[E](expectedClass: Class[E]): E = {
    if (expectedClass.isInstance(effect)) effect.asInstanceOf[E]
    else
      throw new NoSuchElementException(
        "expected effect type [" + expectedClass.getName + "] but found [" + effect.getClass.getName + "]")
  }
}
