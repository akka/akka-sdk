/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.action

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.NotUsed
import akka.actor.ActorSystem
import akka.javasdk.impl.Service
import akka.runtime.sdk.spi.TimerClient
import akka.stream.scaladsl.Source
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.action.ActionCommand
import kalix.protocol.action.ActionResponse
import kalix.protocol.action.Actions

// FIXME remove

private[akka] final class ActionsImpl(
    _system: ActorSystem,
    services: Map[String, Service],
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer)
    extends Actions {

  override def handleUnary(in: ActionCommand): Future[ActionResponse] = ???

  override def handleStreamedIn(in: Source[ActionCommand, NotUsed]): Future[ActionResponse] = ???

  override def handleStreamedOut(in: ActionCommand): Source[ActionResponse, NotUsed] = ???

  override def handleStreamed(in: Source[ActionCommand, NotUsed]): Source[ActionResponse, NotUsed] = ???
}
