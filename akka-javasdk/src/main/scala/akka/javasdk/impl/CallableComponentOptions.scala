/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

trait CallableComponentOptions extends ComponentOptions {

  /**
   * Ask Kalix to forward these headers from the incoming request as metadata headers for the incoming commands. By
   * default no headers except "X-Server-Timing" are forwarded.
   */
  def withForwardHeaders(headers: java.util.Set[String]): ComponentOptions
}
