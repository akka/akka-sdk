/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.effect

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import com.google.protobuf.{ Any => JavaPbAny }
import io.grpc.Status
import kalix.protocol.component.ClientAction

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] sealed trait SecondaryEffectImpl {
  final def replyToClientAction(commandId: Long, errorCode: Option[Status.Code]): Option[ClientAction] = {
    this match {
      case message: MessageReplyImpl[JavaPbAny] @unchecked =>
        Some(ClientAction(ClientAction.Action.Reply(EffectSupport.asProtocol(message))))
      case failure: ErrorReplyImpl[JavaPbAny] @unchecked =>
        val finalErrorCode =
          failure.status
            .orElse(errorCode)
            .getOrElse(Status.Code.UNKNOWN)

        Some(
          ClientAction(
            ClientAction.Action
              .Failure(kalix.protocol.component
                .Failure(commandId, failure.description, grpcStatusCode = finalErrorCode.value()))))
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
private[javasdk] final case class MessageReplyImpl[T](message: T, metadata: Metadata) extends SecondaryEffectImpl {}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ErrorReplyImpl[T](description: String, status: Option[Status.Code])
    extends SecondaryEffectImpl {}
