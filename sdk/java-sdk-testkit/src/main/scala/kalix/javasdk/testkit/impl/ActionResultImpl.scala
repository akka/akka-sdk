/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl

import akka.platform.javasdk.action.Action
import akka.platform.javasdk.impl.action.ActionEffectImpl
import akka.platform.javasdk.testkit.ActionResult

/**
 * INTERNAL API
 */
final class ActionResultImpl[T](effect: ActionEffectImpl.PrimaryEffect) extends ActionResult {

  def this(effect: Action.Effect) = this(effect.asInstanceOf[ActionEffectImpl.PrimaryEffect])

  /** @return true if the call had an effect with a reply, false if not */
  override def isDone(): Boolean = effect.isInstanceOf[ActionEffectImpl.ReplyEffect]

  /** @return true if the call was async, false if not */
  override def isAsync(): Boolean = effect.isInstanceOf[ActionEffectImpl.AsyncEffect]

  /** @return true if the call was an error, false if not */
  override def isError(): Boolean = effect.isInstanceOf[ActionEffectImpl.ErrorEffect]

  override def getError(): String = {
    val error = getEffectOfType(classOf[ActionEffectImpl.ErrorEffect])
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
