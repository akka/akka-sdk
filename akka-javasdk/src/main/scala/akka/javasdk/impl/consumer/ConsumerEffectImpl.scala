/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps

import akka.Done
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.consumer.Consumer

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ConsumerEffectImpl {
  sealed abstract class PrimaryEffect extends Consumer.Effect {}

  final case class ProduceEffect[T](msg: T, metadata: Option[Metadata]) extends PrimaryEffect {}

  case object ConsumedEffect extends PrimaryEffect {}

  final case class AsyncEffect(effect: Future[Consumer.Effect]) extends PrimaryEffect {}

  object Builder extends Consumer.Effect.Builder {
    def produce[S](message: S): Consumer.Effect = ProduceEffect(message, None)

    def produce[S](message: S, metadata: Metadata): Consumer.Effect =
      ProduceEffect(message, Some(metadata))

    def asyncProduce[S](futureMessage: CompletionStage[S]): Consumer.Effect =
      asyncProduce(futureMessage, Metadata.EMPTY)

    def asyncProduce[S](futureMessage: CompletionStage[S], metadata: Metadata): Consumer.Effect =
      AsyncEffect(futureMessage.asScala.map(s => Builder.produce[S](s, metadata))(ExecutionContext.parasitic))

    def asyncEffect(futureEffect: CompletionStage[Consumer.Effect]): Consumer.Effect =
      AsyncEffect(futureEffect.asScala)

    def ignore(): Consumer.Effect =
      ConsumedEffect

    override def done(): Consumer.Effect =
      ConsumedEffect

    override def asyncDone(futureMessage: CompletionStage[Done]): Consumer.Effect =
      AsyncEffect(futureMessage.asScala.map(_ => this.done())(ExecutionContext.parasitic))
  }

  def builder(): Consumer.Effect.Builder = Builder

}
