/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.time.Duration

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.JavaDurationOps

import akka.annotation.InternalApi
import akka.javasdk.impl.RetrySettings.BackoffRetrySettings
import akka.javasdk.impl.RetrySettings.FixedDelayRetrySettings

/**
 * INTERNAL API
 */
@InternalApi
private[akka] sealed trait RetrySettings extends akka.javasdk.RetrySettings {
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final case class RetrySettingsBuilder(attempts: Int)
    extends akka.javasdk.RetrySettings.RetrySettingsBuilder {

  override def withFixedDelay(fixedDelay: Duration): RetrySettings =
    FixedDelayRetrySettings(attempts, fixedDelay.toScala)

  override def withBackoff(minBackoff: Duration, maxBackoff: Duration, randomFactor: Double): RetrySettings =
    BackoffRetrySettings(attempts, minBackoff.toScala, maxBackoff.toScala, randomFactor)

  override def withBackoff(): RetrySettings = {
    // Start with a reasonable minimum backoff (e.g., 100 milliseconds)
    val minBackoff: FiniteDuration = 100.millis

    // Max backoff increases with attempts, capped to a sensible upper limit (e.g., 1 minute)
    val maxBackoff: FiniteDuration = {
      val base = minBackoff * math.pow(2, attempts).toLong
      base.min(1.minute)
    }

    // Random factor can scale slightly with attempts to add more jitter
    val randomFactor: Double = 0.1 + (attempts * 0.05).min(1.0) // cap at 1.0

    BackoffRetrySettings(attempts, minBackoff, maxBackoff, randomFactor)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object RetrySettings {
  case class FixedDelayRetrySettings(attempts: Int, fixedDelay: FiniteDuration) extends RetrySettings
  case class BackoffRetrySettings(
      attempts: Int,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double)
      extends RetrySettings
}
