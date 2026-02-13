/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka

import akka.actor.testkit.typed.internal.{ CapturingAppender => InternalCapturingAppender }

object CapturingAppenderAccess {
  type CapturingAppender = InternalCapturingAppender
  def capturingAppender: CapturingAppender = InternalCapturingAppender.get("")

}
