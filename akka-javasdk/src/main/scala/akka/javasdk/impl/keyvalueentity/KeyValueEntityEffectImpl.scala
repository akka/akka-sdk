/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.annotation.InternalApi
import akka.javasdk.CommandException
import akka.javasdk.Metadata
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.ReplicationFilterImpl
import akka.javasdk.impl.effect.SecondaryEffectImpl
import akka.javasdk.keyvalueentity.KeyValueEntity.Effect
import akka.javasdk.keyvalueentity.KeyValueEntity.Effect.Builder
import akka.javasdk.keyvalueentity.KeyValueEntity.Effect.OnSuccessBuilder
import akka.javasdk.keyvalueentity.KeyValueEntity.ReadOnlyEffect
import akka.javasdk.keyvalueentity.ReplicationFilter

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object KeyValueEntityEffectImpl {
  sealed trait PrimaryEffectImpl[+S]
  final case class UpdateState[S](newState: S, metadata: Option[Metadata]) extends PrimaryEffectImpl[S]
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

  private var _replicationFilter: ReplicationFilter.Builder = ReplicationFilterImpl.empty

  def primaryEffect: PrimaryEffectImpl[S] = _primaryEffect

  def secondaryEffect: SecondaryEffectImpl = _secondaryEffect

  def replFilter: ReplicationFilterImpl =
    _replicationFilter match {
      case impl: ReplicationFilterImpl => impl
      case _                           => throw new IllegalStateException("Unexpected ReplicationFilter implementation")
    }

  override def updateState(newState: S): KeyValueEntityEffectImpl[S] = {
    if (newState == null)
      throw new IllegalStateException("Updated state must not be null")
    _primaryEffect = UpdateState(newState, metadata = None)
    this
  }

  override def updateStateWithMetadata(newState: S, metadata: Metadata): OnSuccessBuilder[S] = {
    if (newState == null)
      throw new IllegalStateException("Updated state must not be null")
    _primaryEffect = UpdateState(newState, Option(metadata))
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
    _secondaryEffect = ErrorReplyImpl(new CommandException(message))
    this.asInstanceOf[KeyValueEntityEffectImpl[T]]
  }

  override def error[T](commandException: CommandException): KeyValueEntityEffectImpl[T] = {
    _secondaryEffect = ErrorReplyImpl(commandException)
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

  override def updateReplicationFilter(filter: ReplicationFilter.Builder): OnSuccessBuilder[S] = {
    _replicationFilter = filter
    this
  }

}
