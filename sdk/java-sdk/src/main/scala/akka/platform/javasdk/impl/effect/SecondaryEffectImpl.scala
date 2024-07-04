/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.effect

import com.google.protobuf.{ Any => JavaPbAny }
import io.grpc.Status
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.impl.MessageCodec
import akka.platform.javasdk.impl.effect
import kalix.protocol.component.ClientAction

sealed trait SecondaryEffectImpl {
  final def replyToClientAction(
      messageCodec: MessageCodec,
      commandId: Long,
      errorCode: Option[Status.Code]): Option[ClientAction] = {
    this match {
      case message: effect.MessageReplyImpl[JavaPbAny] @unchecked =>
        Some(ClientAction(ClientAction.Action.Reply(EffectSupport.asProtocol(message))))
      case failure: effect.ErrorReplyImpl[JavaPbAny] @unchecked =>
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

case object NoSecondaryEffectImpl extends SecondaryEffectImpl {}

final case class MessageReplyImpl[T](message: T, metadata: Metadata) extends SecondaryEffectImpl {}

final case class ErrorReplyImpl[T](description: String, status: Option[Status.Code]) extends SecondaryEffectImpl {}
