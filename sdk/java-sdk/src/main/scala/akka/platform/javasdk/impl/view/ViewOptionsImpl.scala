/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.view

import java.util
import akka.platform.javasdk.view.ViewOptions

/**
 * INTERNAL API
 */
private[akka] final case class ViewOptionsImpl(override val forwardHeaders: java.util.Set[String]) extends ViewOptions {
  def withForwardHeaders(headers: util.Set[String]): ViewOptions = copy(forwardHeaders = headers)
}
