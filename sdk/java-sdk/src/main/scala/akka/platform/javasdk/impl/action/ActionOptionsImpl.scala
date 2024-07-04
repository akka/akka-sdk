/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.action

import java.util
import akka.platform.javasdk.action.ActionOptions

private[akka] final case class ActionOptionsImpl(override val forwardHeaders: java.util.Set[String])
    extends ActionOptions {
  def withForwardHeaders(headers: util.Set[String]): ActionOptions = copy(forwardHeaders = headers)
}
