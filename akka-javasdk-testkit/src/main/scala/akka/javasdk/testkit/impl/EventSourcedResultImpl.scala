/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.SecondaryEffectImpl
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.EmitEvents
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.NoPrimaryEffect
import akka.javasdk.testkit.EventSourcedResult
import io.grpc.Status

import java.util.Collections
import java.util.{ List => JList }
import scala.jdk.CollectionConverters._

/**
 * INTERNAL API
 */
private[akka] object EventSourcedResultImpl {
  def eventsOf[E](effect: EventSourcedEntity.Effect[_]): JList[E] = {
    effect match {
      case ei: EventSourcedEntityEffectImpl[_, E @unchecked] =>
        ei.primaryEffect match {
          case ee: EmitEvents[E @unchecked] => ee.event.toList.asJava
          case _: NoPrimaryEffect.type      => Collections.emptyList()
        }
    }
  }

  def secondaryEffectOf[S](effect: EventSourcedEntity.Effect[_], state: S): SecondaryEffectImpl = {
    effect match {
      case ei: EventSourcedEntityEffectImpl[S @unchecked, _] =>
        ei.secondaryEffect(state)
    }
  }

}

/**
 * INTERNAL API
 */
private[akka] final class EventSourcedResultImpl[R, S, E](
    effect: EventSourcedEntityEffectImpl[S, E],
    state: S,
    secondaryEffect: SecondaryEffectImpl)
    extends EventSourcedResult[R] {
  import EventSourcedResultImpl._

  def this(effect: EventSourcedEntity.Effect[R], state: S, secondaryEffect: SecondaryEffectImpl) =
    this(effect.asInstanceOf[EventSourcedEntityEffectImpl[S, E]], state, secondaryEffect)

  private lazy val eventsIterator = getAllEvents().iterator

  private def secondaryEffectName: String = secondaryEffect match {
    case _: MessageReplyImpl[_] => "reply"
    case _: ErrorReplyImpl[_]   => "error"
    case NoSecondaryEffectImpl  => "no effect" // this should never happen
  }

  /** All emitted events. */
  override def getAllEvents: java.util.List[Any] = eventsOf(effect)

  override def isReply: Boolean = secondaryEffect.isInstanceOf[MessageReplyImpl[_]]

  def getReply: R = secondaryEffect match {
    case MessageReplyImpl(reply, _) => reply.asInstanceOf[R]
    case _ => throw new IllegalStateException(s"The effect was not a reply but [$secondaryEffectName]")
  }

  override def isError: Boolean = secondaryEffect.isInstanceOf[ErrorReplyImpl[_]]

  override def getError: String = secondaryEffect match {
    case ErrorReplyImpl(description, _) => description
    case _ => throw new IllegalStateException(s"The effect was not an error but [$secondaryEffectName]")
  }

  override def getErrorStatusCode: Status.Code = secondaryEffect match {
    case ErrorReplyImpl(_, status) => status.getOrElse(Status.Code.UNKNOWN)
    case _ => throw new IllegalStateException(s"The effect was not an error but [$secondaryEffectName]")
  }

  override def getUpdatedState: AnyRef = state.asInstanceOf[AnyRef]

  override def didEmitEvents(): Boolean = !getAllEvents().isEmpty

  override def getNextEventOfType[T](expectedClass: Class[T]): T =
    if (!eventsIterator.hasNext) throw new NoSuchElementException("No more events found")
    else {
      @SuppressWarnings(Array("unchecked")) val next = eventsIterator.next
      if (expectedClass.isInstance(next)) next.asInstanceOf[T]
      else
        throw new NoSuchElementException(
          "expected event type [" + expectedClass.getName + "] but found [" + next.getClass.getName + "]")
    }
}
