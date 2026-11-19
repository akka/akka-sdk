/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Duration

import akka.annotation.InternalApi
import com.typesafe.config.Config
import com.typesafe.config.ConfigException

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object TaskExpiry {

  private val ExpireAfterKey = "akka.javasdk.agent.task.expire-after"

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
      if (duration.isZero || duration.isNegative) throw invalid(value)
      Some(duration)
    }
  }

  private def invalid(value: String): IllegalArgumentException =
    new IllegalArgumentException(
      s"Invalid value [$value] for [$ExpireAfterKey]. Use a positive duration, for example [30 days], or [off].")
}
