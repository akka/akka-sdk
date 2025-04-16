/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timer

import akka.Done

import java.time.Duration
import scala.jdk.FutureConverters.FutureOps
import akka.annotation.InternalApi
import akka.javasdk.DeferredCall
import akka.javasdk.Metadata
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.client.DeferredCallImpl
import akka.javasdk.timer.TimerScheduler

import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import scala.jdk.DurationConverters.JavaDurationOps

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class TimerSchedulerImpl(val timerClient: akka.runtime.sdk.spi.TimerClient, val metadata: Metadata)
    extends TimerScheduler {

  override def startSingleTimer[I, O](name: String, delay: Duration, deferredCall: DeferredCall[I, O]): Unit =
    startSingleTimer(name, delay, 0, deferredCall)

  override def startSingleTimerAsync[I, O](
      name: String,
      delay: Duration,
      deferredCall: DeferredCall[I, O]): CompletionStage[Done] =
    startSingleTimerAsync(name, delay, 0, deferredCall)

  override def startSingleTimer[I, O](
      name: String,
      delay: Duration,
      maxRetries: Int,
      deferredCall: DeferredCall[I, O]): Unit = {
    try {
      startSingleTimerAsync[I, O](name, delay, maxRetries, deferredCall).toCompletableFuture
        .get() // timeout handled by runtime
    } catch {
      case ex: ExecutionException => throw ErrorHandling.unwrapExecutionException(ex)
    }
  }

  override def startSingleTimerAsync[I, O](
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

  def cancel(name: String): Unit = {
    try {
      cancelAsync(name).toCompletableFuture.get() // timeout handled by runtime
    } catch {
      case ex: ExecutionException => throw ErrorHandling.unwrapExecutionException(ex)
    }
  }

  override def cancelAsync(name: String): CompletionStage[Done] =
    timerClient.removeTimer(name).asJava
}
