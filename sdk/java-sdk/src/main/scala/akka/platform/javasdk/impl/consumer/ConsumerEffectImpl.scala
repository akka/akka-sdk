/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.consumer

import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps

import akka.platform.javasdk.Metadata
import akka.platform.javasdk.consumer.Consumer
import io.grpc.Status

/** INTERNAL API */
object ConsumerEffectImpl {
  sealed abstract class PrimaryEffect[T] extends Consumer.Effect[T] {}

  final case class ReplyEffect[T](msg: T, metadata: Option[Metadata]) extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
  }

  final case class AsyncEffect[T](effect: Future[Consumer.Effect[T]]) extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
  }

  final case class ErrorEffect[T](description: String, statusCode: Option[Status.Code]) extends PrimaryEffect[T] {
    def isEmpty: Boolean = false
  }

  def IgnoreEffect[T](): PrimaryEffect[T] = IgnoreEffect.asInstanceOf[PrimaryEffect[T]]
  case object IgnoreEffect extends PrimaryEffect[Nothing] {
    def isEmpty: Boolean = true
  }

  object Builder extends Consumer.Effect.Builder {
    def reply[S](message: S): Consumer.Effect[S] = ReplyEffect(message, None)

    def reply[S](message: S, metadata: Metadata): Consumer.Effect[S] =
      ReplyEffect(message, Some(metadata))

    def error[S](description: String): Consumer.Effect[S] = ErrorEffect(description, None)

    def asyncReply[S](futureMessage: CompletionStage[S]): Consumer.Effect[S] =
      asyncReply(futureMessage, Metadata.EMPTY)
    def asyncReply[S](futureMessage: CompletionStage[S], metadata: Metadata): Consumer.Effect[S] =
      AsyncEffect(futureMessage.asScala.map(s => Builder.reply[S](s, metadata))(ExecutionContext.parasitic))
    def asyncEffect[S](futureEffect: CompletionStage[Consumer.Effect[S]]): Consumer.Effect[S] =
      AsyncEffect(futureEffect.asScala)
    def ignore[S](): Consumer.Effect[S] =
      IgnoreEffect()
  }

  def builder(): Consumer.Effect.Builder = Builder

}
