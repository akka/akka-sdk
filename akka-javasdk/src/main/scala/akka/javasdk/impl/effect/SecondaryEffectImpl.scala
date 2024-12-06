/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.effect

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import kalix.protocol.component.ClientAction

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] sealed trait SecondaryEffectImpl {
  final def replyToClientAction(commandId: Long): Option[ClientAction] = {
    this match {
      case message: MessageReplyImpl[_] =>
        Some(ClientAction(ClientAction.Action.Reply(EffectSupport.asProtocol(message))))
      case failure: ErrorReplyImpl =>
        Some(
          ClientAction(
            ClientAction.Action
              .Failure(kalix.protocol.component
                .Failure(commandId, failure.description))))
      case NoSecondaryEffectImpl =>
        throw new RuntimeException("No reply or forward returned by command handler!")
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] case object NoSecondaryEffectImpl extends SecondaryEffectImpl {}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class MessageReplyImpl[T](message: T, metadata: Metadata) extends SecondaryEffectImpl {
  if (message == null)
    throw new IllegalArgumentException("Reply must not be null")
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ErrorReplyImpl(description: String) extends SecondaryEffectImpl {}
