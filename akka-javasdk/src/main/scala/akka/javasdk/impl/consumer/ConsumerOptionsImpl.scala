/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import akka.annotation.InternalApi
import akka.javasdk.consumer.ConsumerOptions

import java.util
import java.util.Collections

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class ConsumerOptionsImpl() extends ConsumerOptions {

  /**
   * @return
   *   the headers requested to be forwarded as metadata (cannot be mutated, use withForwardHeaders)
   */
  override def forwardHeaders(): util.Set[String] = Collections.emptySet
}
