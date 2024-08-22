/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.action

import akka.http.javadsl.model.StatusCode
import akka.platform.javasdk.{ HttpResponse, Metadata }
import akka.platform.javasdk.action.Action
import akka.platform.javasdk.impl.StatusCodeConverter
import akka.platform.javasdk.impl.telemetry.Telemetry
import io.grpc.Status

import java.util.concurrent.CompletionStage
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.FutureConverters.CompletionStageOps

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

  class Builder(val messageContextMetadata: Metadata) extends Action.Effect.Builder {
    def reply[S](message: S): Action.Effect[S] = {
      message match {
        case httpResponse: HttpResponse =>
          ReplyEffect(message, Some(Metadata.EMPTY.withStatusCode(httpResponse.getStatusCode).addTracing()))
        case _ => ReplyEffect(message, Some(Metadata.EMPTY.addTracing()))
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
    def error[S](description: String, httpErrorCode: StatusCode): Action.Effect[S] = {
      require(httpErrorCode.isFailure, s"Error effect http error code is not an error: [$httpErrorCode]")
      error(description, StatusCodeConverter.toGrpcCode(httpErrorCode))
    }

    def asyncReply[S](futureMessage: CompletionStage[S]): Action.Effect[S] =
      asyncReply(futureMessage, Metadata.EMPTY)
    def asyncReply[S](futureMessage: CompletionStage[S], metadata: Metadata): Action.Effect[S] =
      AsyncEffect(futureMessage.asScala.map(s => reply[S](s, metadata.addTracing()))(ExecutionContext.parasitic))
    def asyncEffect[S](futureEffect: CompletionStage[Action.Effect[S]]): Action.Effect[S] =
      AsyncEffect(futureEffect.asScala)
    def ignore[S](): Action.Effect[S] =
      IgnoreEffect()

    import scala.jdk.OptionConverters._

    implicit class TracingWrapper(metadata: Metadata) {
      def addTracing(): Metadata = {
        messageContextMetadata.traceContext().traceParent().toScala match {
          case Some(traceparent) if !metadata.has(Telemetry.TRACE_PARENT_KEY) =>
            metadata.add(Telemetry.TRACE_PARENT_KEY, traceparent)
          case _ => metadata
        }
      }
    }
  }

  def builder(messageContextMetadata: Metadata): Action.Effect.Builder = new Builder(messageContextMetadata)

}
