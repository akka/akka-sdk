/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.keyvalueentity

import java.util.Collections
import java.util
import akka.platform.javasdk.keyvalueentity.KeyValueEntityOptions

private[impl] case class KeyValueEntityOptionsImpl(override val forwardHeaders: java.util.Set[String])
    extends KeyValueEntityOptions {

  override def withForwardHeaders(headers: util.Set[String]): KeyValueEntityOptions =
    copy(forwardHeaders = Collections.unmodifiableSet(new util.HashSet(headers)))
}
object KeyValueEntityOptionsImpl {
  val defaults = new KeyValueEntityOptionsImpl(Collections.emptySet())
}
