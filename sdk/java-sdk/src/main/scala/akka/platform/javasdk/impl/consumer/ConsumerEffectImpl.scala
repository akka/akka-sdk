/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.consumer

import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps

import akka.Done
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.consumer.Consumer

/** INTERNAL API */
object ConsumerEffectImpl {
  sealed abstract class PrimaryEffect extends Consumer.Effect {}

  final case class ReplyEffect[T](msg: T, metadata: Option[Metadata]) extends PrimaryEffect {
    def isEmpty: Boolean = false
  }

  final case class AsyncEffect(effect: Future[Consumer.Effect]) extends PrimaryEffect {
    def isEmpty: Boolean = false
  }

  case object IgnoreEffect extends PrimaryEffect {
    def isEmpty: Boolean = true
  }

  object Builder extends Consumer.Effect.Builder {
    def produce[S](message: S): Consumer.Effect = ReplyEffect(message, None)

    def produce[S](message: S, metadata: Metadata): Consumer.Effect =
      ReplyEffect(message, Some(metadata))

    def asyncProduce[S](futureMessage: CompletionStage[S]): Consumer.Effect =
      asyncProduce(futureMessage, Metadata.EMPTY)
    def asyncProduce[S](futureMessage: CompletionStage[S], metadata: Metadata): Consumer.Effect =
      AsyncEffect(futureMessage.asScala.map(s => Builder.produce[S](s, metadata))(ExecutionContext.parasitic))
    def asyncEffect(futureEffect: CompletionStage[Consumer.Effect]): Consumer.Effect =
      AsyncEffect(futureEffect.asScala)
    def ignore(): Consumer.Effect =
      IgnoreEffect

    override def done(): Consumer.Effect =
      ReplyEffect(Done, None)

    override def acyncDone(futureMessage: CompletionStage[Done]): Consumer.Effect =
      AsyncEffect(futureMessage.asScala.map(done => Builder.produce(done))(ExecutionContext.parasitic))
  }

  def builder(): Consumer.Effect.Builder = Builder

}
