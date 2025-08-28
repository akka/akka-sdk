/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.keyvalueentity.KeyValueEntityEffectImpl
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.testkit.KeyValueEntityResult

/**
 * INTERNAL API
 */
private[akka] final class KeyValueEntityResultImpl[R](effect: KeyValueEntityEffectImpl[R])
    extends KeyValueEntityResult[R] {

  def this(effect: KeyValueEntity.Effect[R]) =
    this(effect.asInstanceOf[KeyValueEntityEffectImpl[R]])

  override def isReply(): Boolean = effect.secondaryEffect.isInstanceOf[MessageReplyImpl[_]]

  private def secondaryEffectName: String = effect.secondaryEffect match {
    case _: MessageReplyImpl[_] => "reply"
    case _: ErrorReplyImpl      => "error"
    case NoSecondaryEffectImpl  => "no effect" // this should never happen
  }

  override def getReply(): R = effect.secondaryEffect match {
    case reply: MessageReplyImpl[R @unchecked] => reply.message
    case _ => throw new IllegalStateException(s"The effect was not a reply but [$secondaryEffectName]")
  }

  override def isError(): Boolean = effect.secondaryEffect.isInstanceOf[ErrorReplyImpl]

  override def getError(): String = effect.secondaryEffect match {
    case error: ErrorReplyImpl => error.commandException.getMessage
    case _ => throw new IllegalStateException(s"The effect was not an error but [$secondaryEffectName]")
  }

  override def stateWasUpdated(): Boolean = effect.primaryEffect.isInstanceOf[KeyValueEntityEffectImpl.UpdateState[_]]

  override def getUpdatedState(): Any = effect.primaryEffect match {
    case KeyValueEntityEffectImpl.UpdateState(s, _) => s
    case _ => throw new IllegalStateException("State was not updated by the effect")
  }

  override def stateWasDeleted(): Boolean = effect.primaryEffect eq KeyValueEntityEffectImpl.DeleteEntity

}
