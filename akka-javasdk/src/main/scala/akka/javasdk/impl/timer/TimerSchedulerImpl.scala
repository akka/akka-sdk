/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timer

import java.time.Duration
import java.util.concurrent.CompletionStage

import scala.jdk.DurationConverters.JavaDurationOps
import scala.jdk.FutureConverters.FutureOps

import akka.Done
import akka.annotation.InternalApi
import akka.javasdk.DeferredCall
import akka.javasdk.Metadata
import akka.javasdk.impl.ErrorHandling.unwrapExecutionExceptionCatcher
import akka.javasdk.impl.client.DeferredCallImpl
import akka.javasdk.timer.TimerScheduler

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class TimerSchedulerImpl(val timerClient: akka.runtime.sdk.spi.TimerClient, val metadata: Metadata)
    extends TimerScheduler {

  override def createSingleTimer[I, O](name: String, delay: Duration, deferredCall: DeferredCall[I, O]): Unit =
    createSingleTimer(name, delay, 0, deferredCall)

  override def createSingleTimerAsync[I, O](
      name: String,
      delay: Duration,
      deferredCall: DeferredCall[I, O]): CompletionStage[Done] =
    createSingleTimerAsync(name, delay, 0, deferredCall)

  override def createSingleTimer[I, O](
      name: String,
      delay: Duration,
      maxRetries: Int,
      deferredCall: DeferredCall[I, O]): Unit = {
    try {
      createSingleTimerAsync[I, O](name, delay, maxRetries, deferredCall).toCompletableFuture
        .get() // timeout handled by runtime
    } catch unwrapExecutionExceptionCatcher
  }

  override def createSingleTimerAsync[I, O](
      name: String,
      delay: Duration,
      maxRetries: Int,
      deferredCall: DeferredCall[I, O]): CompletionStage[Done] =
    deferredCall match {
      case embeddedDeferredCall: DeferredCallImpl[I, O] =>
        timerClient
          .startSingleTimer(name, delay.toScala, maxRetries, embeddedDeferredCall.deferredRequest())
          .asJava
      case unknown =>
        // should never happen, but needs to make compiler happy
        throw new IllegalStateException(s"Unknown DeferredCall implementation: $unknown")
    }

  def delete(name: String): Unit = {
    try {
      deleteAsync(name).toCompletableFuture.get() // timeout handled by runtime
    } catch unwrapExecutionExceptionCatcher
  }

  override def deleteAsync(name: String): CompletionStage[Done] =
    timerClient.removeTimer(name).asJava

  override def startSingleTimer[I, O](
      name: String,
      delay: Duration,
      deferredCall: DeferredCall[I, O]): CompletionStage[Done] =
    createSingleTimerAsync(name, delay, deferredCall)

  override def startSingleTimer[I, O](
      name: String,
      delay: Duration,
      maxRetries: Int,
      deferredCall: DeferredCall[I, O]): CompletionStage[Done] =
    createSingleTimerAsync(name, delay, maxRetries, deferredCall)

  override def cancel(name: String): CompletionStage[Done] =
    deleteAsync(name)
}
