/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.impl.timedaction.TimedActionEffectImpl
import akka.javasdk.testkit.TimedActionResult
import akka.javasdk.timedaction.TimedAction

/**
 * INTERNAL API
 */
final class TimedActionResultImpl[T](effect: TimedActionEffectImpl.PrimaryEffect) extends TimedActionResult {

  def this(effect: TimedAction.Effect) = this(effect.asInstanceOf[TimedActionEffectImpl.PrimaryEffect])

  /** @return true if the call had an effect with a reply, false if not */
  override def isDone(): Boolean = effect == TimedActionEffectImpl.SuccessEffect

  /** @return true if the call was async, false if not */
  override def isAsync(): Boolean = effect.isInstanceOf[TimedActionEffectImpl.AsyncEffect]

  /** @return true if the call was an error, false if not */
  override def isError(): Boolean = effect.isInstanceOf[TimedActionEffectImpl.ErrorEffect]

  override def getError(): String = {
    val error = getEffectOfType(classOf[TimedActionEffectImpl.ErrorEffect])
    error.description
  }

  /**
   * Look at effect and verifies that it is of type E or fail if not.
   *
   * @return
   *   The next effect if it is of type E, for additional assertions.
   */
  private def getEffectOfType[E](expectedClass: Class[E]): E = {
    if (expectedClass.isInstance(effect)) effect.asInstanceOf[E]
    else
      throw new NoSuchElementException(
        "expected effect type [" + expectedClass.getName + "] but found [" + effect.getClass.getName + "]")
  }

}
