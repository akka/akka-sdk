/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.keyvalueentity

import akka.platform.javasdk.Metadata
import akka.platform.javasdk.impl.effect.ErrorReplyImpl
import akka.platform.javasdk.impl.effect.MessageReplyImpl
import akka.platform.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.platform.javasdk.impl.effect.SecondaryEffectImpl
import akka.platform.javasdk.keyvalueentity.KeyValueEntity.Effect
import akka.platform.javasdk.keyvalueentity.KeyValueEntity.Effect.Builder
import akka.platform.javasdk.keyvalueentity.KeyValueEntity.Effect.OnSuccessBuilder

object KeyValueEntityEffectImpl {
  sealed trait PrimaryEffectImpl[+S]
  final case class UpdateState[S](newState: S) extends PrimaryEffectImpl[S]
  case object DeleteEntity extends PrimaryEffectImpl[Nothing]
  case object NoPrimaryEffect extends PrimaryEffectImpl[Nothing]
}

class KeyValueEntityEffectImpl[S] extends Builder[S] with OnSuccessBuilder[S] with Effect[S] {
  import KeyValueEntityEffectImpl._

  private var _primaryEffect: PrimaryEffectImpl[S] = NoPrimaryEffect
  private var _secondaryEffect: SecondaryEffectImpl = NoSecondaryEffectImpl

  def primaryEffect: PrimaryEffectImpl[S] = _primaryEffect

  def secondaryEffect: SecondaryEffectImpl = _secondaryEffect

  override def updateState(newState: S): KeyValueEntityEffectImpl[S] = {
    _primaryEffect = UpdateState(newState)
    this
  }

  override def deleteEntity(): KeyValueEntityEffectImpl[S] = {
    _primaryEffect = DeleteEntity
    this
  }

  override def reply[T](message: T): KeyValueEntityEffectImpl[T] =
    reply(message, Metadata.EMPTY)

  override def reply[T](message: T, metadata: Metadata): KeyValueEntityEffectImpl[T] = {
    _secondaryEffect = MessageReplyImpl(message, metadata)
    this.asInstanceOf[KeyValueEntityEffectImpl[T]]
  }

  override def error[T](description: String): KeyValueEntityEffectImpl[T] = {
    _secondaryEffect = ErrorReplyImpl(description, None)
    this.asInstanceOf[KeyValueEntityEffectImpl[T]]
  }

  def hasError(): Boolean =
    _secondaryEffect.isInstanceOf[ErrorReplyImpl[_]]

  override def thenReply[T](message: T): KeyValueEntityEffectImpl[T] =
    thenReply(message, Metadata.EMPTY)

  override def thenReply[T](message: T, metadata: Metadata): KeyValueEntityEffectImpl[T] = {
    _secondaryEffect = MessageReplyImpl(message, metadata)
    this.asInstanceOf[KeyValueEntityEffectImpl[T]]
  }

}
