/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

trait ComponentOptions {

  /**
   * @return
   *   the headers requested to be forwarded as metadata (cannot be mutated, use withForwardHeaders)
   */
  def forwardHeaders(): java.util.Set[String]
}
