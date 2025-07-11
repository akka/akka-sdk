/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.UserException
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.SecondaryEffectImpl
import akka.javasdk.keyvalueentity.KeyValueEntity.Effect
import akka.javasdk.keyvalueentity.KeyValueEntity.Effect.Builder
import akka.javasdk.keyvalueentity.KeyValueEntity.Effect.OnSuccessBuilder
import akka.javasdk.keyvalueentity.KeyValueEntity.ReadOnlyEffect

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object KeyValueEntityEffectImpl {
  sealed trait PrimaryEffectImpl[+S]
  final case class UpdateState[S](newState: S) extends PrimaryEffectImpl[S]
  case object DeleteEntity extends PrimaryEffectImpl[Nothing]
  case object NoPrimaryEffect extends PrimaryEffectImpl[Nothing]
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class KeyValueEntityEffectImpl[S]
    extends Builder[S]
    with OnSuccessBuilder[S]
    with Effect[S]
    with ReadOnlyEffect[S] {
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

  override def error[T](message: String): KeyValueEntityEffectImpl[T] = {
    _secondaryEffect = ErrorReplyImpl(new UserException(message))
    this.asInstanceOf[KeyValueEntityEffectImpl[T]]
  }

  override def error[T](userException: UserException): KeyValueEntityEffectImpl[T] = {
    _secondaryEffect = ErrorReplyImpl(userException)
    this.asInstanceOf[KeyValueEntityEffectImpl[T]]
  }

  def hasError(): Boolean =
    _secondaryEffect.isInstanceOf[ErrorReplyImpl]

  override def thenReply[T](message: T): KeyValueEntityEffectImpl[T] =
    thenReply(message, Metadata.EMPTY)

  override def thenReply[T](message: T, metadata: Metadata): KeyValueEntityEffectImpl[T] = {
    _secondaryEffect = MessageReplyImpl(message, metadata)
    this.asInstanceOf[KeyValueEntityEffectImpl[T]]
  }

}
