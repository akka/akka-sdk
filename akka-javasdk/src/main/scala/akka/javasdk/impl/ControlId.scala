/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueType

/**
 * INTERNAL API
 *
 * The id of the control that a guardrail, sanitizer or evaluator entry implements.
 */
@InternalApi private[javasdk] object ControlId {

  val Key = "control-id"

  /**
   * The `control-id` of an entry, or `None` when the entry has none.
   *
   * @param entry
   *   the entry, as named in the error message, such as `Sanitizer [pii]`
   * @throws IllegalArgumentException
   *   if the value is not a string or is blank
   */
  def read(config: Config, entry: String): Option[String] =
    if (!config.hasPath(Key)) None
    else {
      val value = config.getValue(Key)
      if (value.valueType != ConfigValueType.STRING)
        throw new IllegalArgumentException(s"$entry must define [$Key] as a string, but defines [${value.valueType}]")
      val id = config.getString(Key)
      if (id.isBlank)
        throw new IllegalArgumentException(s"$entry must define a non blank [$Key]")
      Some(id)
    }
}
