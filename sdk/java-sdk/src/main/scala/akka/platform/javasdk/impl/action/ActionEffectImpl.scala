/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.action

import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps

import akka.Done
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.action.Action

/** INTERNAL API */
object ActionEffectImpl {
  sealed abstract class PrimaryEffect extends Action.Effect {}

  final case class ReplyEffect(metadata: Option[Metadata]) extends PrimaryEffect {
    def isEmpty: Boolean = false
  }

  final case class AsyncEffect(effect: Future[Action.Effect]) extends PrimaryEffect {
    def isEmpty: Boolean = false
  }

  final case class ErrorEffect(description: String) extends PrimaryEffect {
    def isEmpty: Boolean = false
  }

  class Builder extends Action.Effect.Builder {
    def done(): Action.Effect = {
      ReplyEffect(None)
    }
    def error(description: String): Action.Effect = ErrorEffect(description)

    def asyncDone(futureMessage: CompletionStage[Done]): Action.Effect =
      AsyncEffect(futureMessage.asScala.map(_ => done())(ExecutionContext.parasitic))

    def asyncEffect(futureEffect: CompletionStage[Action.Effect]): Action.Effect =
      AsyncEffect(futureEffect.asScala)
  }

  def builder(): Action.Effect.Builder = new Builder()

}
