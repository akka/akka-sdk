/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl

import akka.platform.javasdk.impl.effect.ErrorReplyImpl
import akka.platform.javasdk.impl.effect.MessageReplyImpl
import akka.platform.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.platform.javasdk.impl.valueentity.ValueEntityEffectImpl
import akka.platform.javasdk.testkit.ValueEntityResult
import akka.platform.javasdk.valueentity.ValueEntity

import io.grpc.Status

/**
 * INTERNAL API
 */
private[akka] final class ValueEntityResultImpl[R](effect: ValueEntityEffectImpl[R]) extends ValueEntityResult[R] {

  def this(effect: ValueEntity.Effect[R]) =
    this(effect.asInstanceOf[ValueEntityEffectImpl[R]])

  override def isReply(): Boolean = effect.secondaryEffect.isInstanceOf[MessageReplyImpl[_]]

  private def secondaryEffectName: String = effect.secondaryEffect match {
    case _: MessageReplyImpl[_] => "reply"
    case _: ErrorReplyImpl[_]   => "error"
    case NoSecondaryEffectImpl  => "no effect" // this should never happen
  }

  override def getReply(): R = effect.secondaryEffect match {
    case reply: MessageReplyImpl[R @unchecked] => reply.message
    case _ => throw new IllegalStateException(s"The effect was not a reply but [$secondaryEffectName]")
  }

  override def isError(): Boolean = effect.secondaryEffect.isInstanceOf[ErrorReplyImpl[_]]

  override def getError(): String = effect.secondaryEffect match {
    case error: ErrorReplyImpl[_] => error.description
    case _ => throw new IllegalStateException(s"The effect was not an error but [$secondaryEffectName]")
  }

  override def getErrorStatusCode: Status.Code = effect.secondaryEffect match {
    case ErrorReplyImpl(_, status) => status.getOrElse(Status.Code.UNKNOWN)
    case _ => throw new IllegalStateException(s"The effect was not an error but [$secondaryEffectName]")
  }

  override def stateWasUpdated(): Boolean = effect.primaryEffect.isInstanceOf[ValueEntityEffectImpl.UpdateState[_]]

  override def getUpdatedState(): Any = effect.primaryEffect match {
    case ValueEntityEffectImpl.UpdateState(s) => s
    case _                                    => throw new IllegalStateException("State was not updated by the effect")
  }

  override def stateWasDeleted(): Boolean = effect.primaryEffect eq ValueEntityEffectImpl.DeleteEntity

}
