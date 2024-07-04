/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.valueentity

import java.util.Collections
import java.util
import akka.platform.javasdk.valueentity.ValueEntityOptions

private[impl] case class ValueEntityOptionsImpl(override val forwardHeaders: java.util.Set[String])
    extends ValueEntityOptions {

  override def withForwardHeaders(headers: util.Set[String]): ValueEntityOptions =
    copy(forwardHeaders = Collections.unmodifiableSet(new util.HashSet(headers)))
}
object ValueEntityOptionsImpl {
  val defaults = new ValueEntityOptionsImpl(Collections.emptySet())
}
