/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.SecondaryEffectImpl
import java.util
import java.util.function.{ Function => JFunction }

import scala.jdk.CollectionConverters._

import akka.javasdk.eventsourcedentity.EventSourcedEntity.Effect
import akka.javasdk.eventsourcedentity.EventSourcedEntity.Effect.Builder
import akka.javasdk.eventsourcedentity.EventSourcedEntity.Effect.OnSuccessBuilder
import akka.javasdk.eventsourcedentity.EventSourcedEntity.ReadOnlyEffect

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object EventSourcedEntityEffectImpl {
  sealed trait PrimaryEffectImpl
  final case class EmitEvents[E](event: Iterable[E], deleteEntity: Boolean = false) extends PrimaryEffectImpl
  case object NoPrimaryEffect extends PrimaryEffectImpl
}

// Note: Effect and ReadOnlyEffect both implemented here, so not possible to identify at runtime, but all we need
//       for now is compile time/type level identification if a command handler is read only or not.
/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] class EventSourcedEntityEffectImpl[S, E]
    extends Builder[S, E]
    with OnSuccessBuilder[S]
    with Effect[S]
    with ReadOnlyEffect[S] {
  import EventSourcedEntityEffectImpl._

  private var _primaryEffect: PrimaryEffectImpl = NoPrimaryEffect
  private var _secondaryEffect: SecondaryEffectImpl = NoSecondaryEffectImpl

  private var _functionSecondaryEffect: Function[S, SecondaryEffectImpl] = _ => NoSecondaryEffectImpl

  def primaryEffect: PrimaryEffectImpl = _primaryEffect

  def secondaryEffect(state: S): SecondaryEffectImpl =
    _functionSecondaryEffect(state) match {
      case NoSecondaryEffectImpl => _secondaryEffect
      case newSecondary          => newSecondary
    }

  override def persist(event: E): EventSourcedEntityEffectImpl[S, E] =
    persistAll(Vector(event))

  override def persist(event1: E, event2: E, events: E*): OnSuccessBuilder[S] = {
    val builder = Vector.newBuilder[E]
    builder += event1
    builder += event2
    builder ++= events
    persistAll(builder.result())
  }

  override def persistAll(events: util.List[_ <: E]): EventSourcedEntityEffectImpl[S, E] =
    persistAll(events.asScala)

  private def persistAll(events: Iterable[_ <: E]): EventSourcedEntityEffectImpl[S, E] = {
    _primaryEffect = EmitEvents(events)
    this
  }

  override def deleteEntity(): EventSourcedEntityEffectImpl[S, E] = {
    _primaryEffect = _primaryEffect match {
      case NoPrimaryEffect           => EmitEvents[E](Vector.empty, deleteEntity = true)
      case emitEvents: EmitEvents[_] => emitEvents.copy(deleteEntity = true)
    }
    this
  }

  override def reply[T](message: T): EventSourcedEntityEffectImpl[T, E] =
    reply(message, Metadata.EMPTY)

  override def reply[T](message: T, metadata: Metadata): EventSourcedEntityEffectImpl[T, E] = {
    _secondaryEffect = MessageReplyImpl(message, metadata)
    this.asInstanceOf[EventSourcedEntityEffectImpl[T, E]]
  }

  override def error[T](description: String): EventSourcedEntityEffectImpl[T, E] = {
    _secondaryEffect = ErrorReplyImpl(description)
    this.asInstanceOf[EventSourcedEntityEffectImpl[T, E]]
  }

  override def thenReply[T](replyMessage: JFunction[S, T]): EventSourcedEntityEffectImpl[T, E] =
    thenReply(replyMessage, Metadata.EMPTY)

  override def thenReply[T](replyMessage: JFunction[S, T], metadata: Metadata): EventSourcedEntityEffectImpl[T, E] = {
    _functionSecondaryEffect = state => MessageReplyImpl(replyMessage.apply(state), metadata)
    this.asInstanceOf[EventSourcedEntityEffectImpl[T, E]]
  }

}
