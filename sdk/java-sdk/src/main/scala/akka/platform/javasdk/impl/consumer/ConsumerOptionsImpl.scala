/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.consumer

import java.util
import java.util.Collections

import akka.platform.javasdk.consumer.ConsumerOptions

private[akka] final case class ConsumerOptionsImpl() extends ConsumerOptions {

  /**
   * @return
   *   the headers requested to be forwarded as metadata (cannot be mutated, use withForwardHeaders)
   */
  override def forwardHeaders(): util.Set[String] = Collections.emptySet
}
