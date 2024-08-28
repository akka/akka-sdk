/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import akka.Done
import akka.javasdk.Metadata
import akka.javasdk.timedaction.TimedAction

import java.util.concurrent.CompletionStage
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps

/** INTERNAL API */
object TimedActionEffectImpl {
  sealed abstract class PrimaryEffect extends TimedAction.Effect {}

  final case class ReplyEffect(metadata: Option[Metadata]) extends PrimaryEffect {
    def isEmpty: Boolean = false
  }

  final case class AsyncEffect(effect: Future[TimedAction.Effect]) extends PrimaryEffect {
    def isEmpty: Boolean = false
  }

  final case class ErrorEffect(description: String) extends PrimaryEffect {
    def isEmpty: Boolean = false
  }

  class Builder extends TimedAction.Effect.Builder {
    def done(): TimedAction.Effect = {
      ReplyEffect(None)
    }
    def error(description: String): TimedAction.Effect = ErrorEffect(description)

    def asyncDone(futureMessage: CompletionStage[Done]): TimedAction.Effect =
      AsyncEffect(futureMessage.asScala.map(_ => done())(ExecutionContext.parasitic))

    def asyncEffect(futureEffect: CompletionStage[TimedAction.Effect]): TimedAction.Effect =
      AsyncEffect(futureEffect.asScala)
  }

  def builder(): TimedAction.Effect.Builder = new Builder()

}
