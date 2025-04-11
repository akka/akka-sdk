/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timer

import java.time.Duration
import scala.jdk.FutureConverters.FutureOps
import akka.annotation.InternalApi
import akka.javasdk.DeferredCall
import akka.javasdk.Metadata
import akka.javasdk.impl.client.DeferredCallImpl
import akka.javasdk.timer.TimerScheduler

import scala.jdk.DurationConverters.JavaDurationOps

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class TimerSchedulerImpl(val timerClient: akka.runtime.sdk.spi.TimerClient, val metadata: Metadata)
    extends TimerScheduler {

  override def startSingleTimer[I, O](name: String, delay: Duration, deferredCall: DeferredCall[I, O]): Unit =
    startSingleTimer(name, delay, 0, deferredCall)

  override def startSingleTimer[I, O](
      name: String,
      delay: Duration,
      maxRetries: Int,
      deferredCall: DeferredCall[I, O]): Unit = {

    deferredCall match {
      case embeddedDeferredCall: DeferredCallImpl[I, O] =>
        timerClient
          .startSingleTimer(name, delay.toScala, maxRetries, embeddedDeferredCall.deferredRequest())
          .asJava
          .toCompletableFuture
          .get() // timeout handled by runtime

      case unknown =>
        // should never happen, but needs to make compiler happy
        throw new IllegalStateException(s"Unknown DeferredCall implementation: $unknown")
    }

  }

  def cancel(name: String): Unit = {
    timerClient.removeTimer(name).asJava.toCompletableFuture.get() // timeout handled by runtime
  }

}
