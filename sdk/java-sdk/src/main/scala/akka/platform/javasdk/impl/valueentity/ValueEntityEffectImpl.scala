/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.valueentity

import akka.platform.javasdk.Metadata
import akka.platform.javasdk.impl.effect.ErrorReplyImpl
import akka.platform.javasdk.impl.effect.MessageReplyImpl
import akka.platform.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.platform.javasdk.impl.effect.SecondaryEffectImpl
import akka.platform.javasdk.valueentity.ValueEntity.Effect
import akka.platform.javasdk.valueentity.ValueEntity.Effect.Builder
import akka.platform.javasdk.valueentity.ValueEntity.Effect.OnSuccessBuilder

object ValueEntityEffectImpl {
  sealed trait PrimaryEffectImpl[+S]
  final case class UpdateState[S](newState: S) extends PrimaryEffectImpl[S]
  case object DeleteEntity extends PrimaryEffectImpl[Nothing]
  case object NoPrimaryEffect extends PrimaryEffectImpl[Nothing]
}

class ValueEntityEffectImpl[S] extends Builder[S] with OnSuccessBuilder[S] with Effect[S] {
  import ValueEntityEffectImpl._

  private var _primaryEffect: PrimaryEffectImpl[S] = NoPrimaryEffect
  private var _secondaryEffect: SecondaryEffectImpl = NoSecondaryEffectImpl

  def primaryEffect: PrimaryEffectImpl[S] = _primaryEffect

  def secondaryEffect: SecondaryEffectImpl = _secondaryEffect

  override def updateState(newState: S): ValueEntityEffectImpl[S] = {
    _primaryEffect = UpdateState(newState)
    this
  }

  override def deleteEntity(): ValueEntityEffectImpl[S] = {
    _primaryEffect = DeleteEntity
    this
  }

  override def reply[T](message: T): ValueEntityEffectImpl[T] =
    reply(message, Metadata.EMPTY)

  override def reply[T](message: T, metadata: Metadata): ValueEntityEffectImpl[T] = {
    _secondaryEffect = MessageReplyImpl(message, metadata)
    this.asInstanceOf[ValueEntityEffectImpl[T]]
  }

  override def error[T](description: String): ValueEntityEffectImpl[T] = {
    _secondaryEffect = ErrorReplyImpl(description, None)
    this.asInstanceOf[ValueEntityEffectImpl[T]]
  }

  def hasError(): Boolean =
    _secondaryEffect.isInstanceOf[ErrorReplyImpl[_]]

  override def thenReply[T](message: T): ValueEntityEffectImpl[T] =
    thenReply(message, Metadata.EMPTY)

  override def thenReply[T](message: T, metadata: Metadata): ValueEntityEffectImpl[T] = {
    _secondaryEffect = MessageReplyImpl(message, metadata)
    this.asInstanceOf[ValueEntityEffectImpl[T]]
  }

}
