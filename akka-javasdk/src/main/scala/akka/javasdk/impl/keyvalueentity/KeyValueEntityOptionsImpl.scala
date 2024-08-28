/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.javasdk.keyvalueentity.KeyValueEntityOptions
import java.util.Collections
import java.util

private[impl] case class KeyValueEntityOptionsImpl(override val forwardHeaders: java.util.Set[String])
    extends KeyValueEntityOptions {

  override def withForwardHeaders(headers: util.Set[String]): KeyValueEntityOptions =
    copy(forwardHeaders = Collections.unmodifiableSet(new util.HashSet(headers)))
}
object KeyValueEntityOptionsImpl {
  val defaults = new KeyValueEntityOptionsImpl(Collections.emptySet())
}
