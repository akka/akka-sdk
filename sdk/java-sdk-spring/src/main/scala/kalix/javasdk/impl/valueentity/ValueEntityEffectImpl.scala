/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.valueentity

import java.util

import scala.jdk.CollectionConverters._

import kalix.javasdk.DeferredCall
import kalix.javasdk.Metadata
import kalix.javasdk.SideEffect
import kalix.javasdk.impl.effect.ErrorReplyImpl
import kalix.javasdk.impl.effect.ForwardReplyImpl
import kalix.javasdk.impl.effect.MessageReplyImpl
import kalix.javasdk.impl.effect.NoSecondaryEffectImpl
import kalix.javasdk.impl.effect.SecondaryEffectImpl
import kalix.javasdk.valueentity.ValueEntity.Effect
import kalix.javasdk.valueentity.ValueEntity.Effect.Builder
import kalix.javasdk.valueentity.ValueEntity.Effect.OnSuccessBuilder

object ValueEntityEffectImpl {
  sealed trait PrimaryEffectImpl[+S]
  final case class UpdateState[S](newState: S) extends PrimaryEffectImpl[S]
  case object DeleteEntity extends PrimaryEffectImpl[Nothing]
  case object NoPrimaryEffect extends PrimaryEffectImpl[Nothing]
}

class ValueEntityEffectImpl[S] extends Builder[S] with OnSuccessBuilder[S] with Effect[S] {
  import ValueEntityEffectImpl._

  private var _primaryEffect: PrimaryEffectImpl[S] = NoPrimaryEffect
  private var _secondaryEffect: SecondaryEffectImpl = NoSecondaryEffectImpl()

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

  override def deleteState(): ValueEntityEffectImpl[S] =
    deleteEntity()

  override def reply[T](message: T): ValueEntityEffectImpl[T] =
    reply(message, Metadata.EMPTY)

  override def reply[T](message: T, metadata: Metadata): ValueEntityEffectImpl[T] = {
    _secondaryEffect = MessageReplyImpl(message, metadata, _secondaryEffect.sideEffects)
    this.asInstanceOf[ValueEntityEffectImpl[T]]
  }

  override def forward[T](serviceCall: DeferredCall[_, T]): ValueEntityEffectImpl[T] = {
    _secondaryEffect = ForwardReplyImpl(serviceCall, _secondaryEffect.sideEffects)
    this.asInstanceOf[ValueEntityEffectImpl[T]]
  }

  override def error[T](description: String): ValueEntityEffectImpl[T] = {
    _secondaryEffect = ErrorReplyImpl(description, None, _secondaryEffect.sideEffects)
    this.asInstanceOf[ValueEntityEffectImpl[T]]
  }

  def hasError(): Boolean =
    _secondaryEffect.isInstanceOf[ErrorReplyImpl[_]]

  override def thenReply[T](message: T): ValueEntityEffectImpl[T] =
    thenReply(message, Metadata.EMPTY)

  override def thenReply[T](message: T, metadata: Metadata): ValueEntityEffectImpl[T] = {
    _secondaryEffect = MessageReplyImpl(message, metadata, _secondaryEffect.sideEffects)
    this.asInstanceOf[ValueEntityEffectImpl[T]]
  }

  override def thenForward[T](serviceCall: DeferredCall[_, T]): ValueEntityEffectImpl[T] = {
    _secondaryEffect = ForwardReplyImpl(serviceCall, _secondaryEffect.sideEffects)
    this.asInstanceOf[ValueEntityEffectImpl[T]]
  }

  override def addSideEffects(sideEffects: util.Collection[SideEffect]): ValueEntityEffectImpl[S] = {
    _secondaryEffect = _secondaryEffect.addSideEffects(sideEffects.asScala)
    this
  }

  override def addSideEffects(sideEffects: SideEffect*): ValueEntityEffectImpl[S] = {
    _secondaryEffect = _secondaryEffect.addSideEffects(sideEffects)
    this
  }
}
