/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import scala.annotation.nowarn

import com.fasterxml.jackson.annotation.JsonIgnore

@nowarn("msg=never used")
abstract class ThrowableMixin {
  @JsonIgnore
  private var detailMessage: String = _

  @JsonIgnore
  private var cause: Throwable = _

  @JsonIgnore
  private var stackTrace: Array[StackTraceElement] = _

  @JsonIgnore
  private var suppressedExceptions: java.util.List[Throwable] = _;

  @JsonIgnore
  def getSuppressed(): Array[Throwable]

  @JsonIgnore
  def getLocalizedMessage(): String

}
