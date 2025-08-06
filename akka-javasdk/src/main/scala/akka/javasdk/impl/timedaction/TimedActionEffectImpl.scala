/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps

import akka.Done
import akka.annotation.InternalApi
import akka.javasdk.timedaction.TimedAction

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object TimedActionEffectImpl {
  sealed abstract class PrimaryEffect extends TimedAction.Effect {}

  object SuccessEffect extends PrimaryEffect {}

  final case class AsyncEffect(effect: Future[TimedAction.Effect]) extends PrimaryEffect {}

  final case class ErrorEffect(description: String) extends PrimaryEffect {}

  class Builder extends TimedAction.Effect.Builder {
    def done(): TimedAction.Effect = {
      SuccessEffect
    }
    def error(description: String): TimedAction.Effect = ErrorEffect(description)

    def asyncDone(futureMessage: CompletionStage[Done]): TimedAction.Effect =
      AsyncEffect(futureMessage.asScala.map(_ => done())(ExecutionContext.parasitic))

    def asyncEffect(futureEffect: CompletionStage[TimedAction.Effect]): TimedAction.Effect =
      AsyncEffect(futureEffect.asScala)
  }

  def builder(): TimedAction.Effect.Builder = new Builder()

}
