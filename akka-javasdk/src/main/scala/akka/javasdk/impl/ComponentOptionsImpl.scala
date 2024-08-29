/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi

import java.util
import java.util.Collections

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class ComponentOptionsImpl(override val forwardHeaders: java.util.Set[String])
    extends CallableComponentOptions {

  override def withForwardHeaders(headers: util.Set[String]): ComponentOptions =
    copy(forwardHeaders = Collections.unmodifiableSet(new util.HashSet(headers)));
}
