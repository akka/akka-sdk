/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.action

import java.util.concurrent.CompletionStage

import io.grpc.Status
import akka.platform.javasdk.StatusCode.ErrorCode
import akka.platform.javasdk.impl.StatusCodeConverter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps
import akka.platform.javasdk.HttpResponse
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.action.Action

/** INTERNAL API */
object ActionEffectImpl {
  sealed abstract class PrimaryEffect[T] extends Action.Effect[T] {}

  final case class ReplyEffect[T](msg: T, metadata: Option[Metadata]) extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
  }

  final case class AsyncEffect[T](effect: Future[Action.Effect[T]]) extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
  }

  final case class ErrorEffect[T](description: String, statusCode: Option[Status.Code]) extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
  }

  def IgnoreEffect[T](): PrimaryEffect[T] = IgnoreEffect.asInstanceOf[PrimaryEffect[T]]
  case object IgnoreEffect extends PrimaryEffect[Nothing] {
    def isEmpty: Boolean = true
  }

  object Builder extends Action.Effect.Builder {
    def reply[S](message: S): Action.Effect[S] = {
      message match {
        case httpResponse: HttpResponse =>
          ReplyEffect(message, Some(Metadata.EMPTY.withStatusCode(httpResponse.getStatusCode)))
        case _ => ReplyEffect(message, None)
      }
    }
    def reply[S](message: S, metadata: Metadata): Action.Effect[S] = {
      message match {
        case httpResponse: HttpResponse =>
          ReplyEffect(message, Some(metadata.withStatusCode(httpResponse.getStatusCode)))
        case _ => ReplyEffect(message, Some(metadata))
      }
      ReplyEffect(message, Some(metadata))
    }
    def error[S](description: String): Action.Effect[S] = ErrorEffect(description, None)
    def error[S](description: String, grpcErrorCode: Status.Code): Action.Effect[S] = {
      if (grpcErrorCode.toStatus.isOk) throw new IllegalArgumentException("Cannot fail with a success status")
      ErrorEffect(description, Some(grpcErrorCode))
    }
    def error[S](description: String, httpErrorCode: ErrorCode): Action.Effect[S] =
      error(description, StatusCodeConverter.toGrpcCode(httpErrorCode))
    def asyncReply[S](futureMessage: CompletionStage[S]): Action.Effect[S] =
      asyncReply(futureMessage, Metadata.EMPTY)
    def asyncReply[S](futureMessage: CompletionStage[S], metadata: Metadata): Action.Effect[S] =
      AsyncEffect(futureMessage.asScala.map(s => Builder.reply[S](s, metadata))(ExecutionContext.parasitic))
    def asyncEffect[S](futureEffect: CompletionStage[Action.Effect[S]]): Action.Effect[S] =
      AsyncEffect(futureEffect.asScala)
    def ignore[S](): Action.Effect[S] =
      IgnoreEffect()
  }

  def builder(): Action.Effect.Builder = Builder

}
