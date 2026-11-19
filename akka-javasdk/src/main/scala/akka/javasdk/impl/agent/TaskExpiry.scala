/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Duration
import java.time.Instant

import akka.annotation.InternalApi
import com.typesafe.config.Config
import com.typesafe.config.ConfigException

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object TaskExpiry {

  private val ExpireAfterKey = "akka.javasdk.agent.task.expire-after"

  // The runtime adds the duration to the time when the task ends, and stores the sum in a timestamp
  // column. A sum out of the range of Instant or of that column fails the persist that ends the task,
  // so the task could never end. The year 9999 is inside both ranges.
  private val LatestExpiry = Instant.parse("9999-12-31T23:59:59Z")

  /** The error message for an expireAfter value that [[isValid]] rejects. */
  def invalidMessage(expireAfter: Duration): String =
    s"expireAfter must be a positive duration that ends before the year 10000, was [$expireAfter]"

  /** A positive duration that, counted from now, ends before the year 10000. */
  def isValid(expireAfter: Duration): Boolean =
    expireAfter != null && !expireAfter.isZero && !expireAfter.isNegative &&
    expireAfter.compareTo(Duration.between(Instant.now(), LatestExpiry)) <= 0

  /** The default expiry of a task created without its own, or None when it is off. */
  def defaultExpireAfter(config: Config): Option[Duration] = {
    val value = config.getString(ExpireAfterKey)
    if (value.trim.equalsIgnoreCase("off")) None
    else {
      val duration =
        try config.getDuration(ExpireAfterKey)
        catch {
          case _: ConfigException => throw invalid(value)
        }
      if (!isValid(duration)) throw invalid(value)
      Some(duration)
    }
  }

  private def invalid(value: String): IllegalArgumentException =
    new IllegalArgumentException(
      s"Invalid value [$value] for [$ExpireAfterKey]. Use a positive duration, for example [30 days], or [off].")
}
