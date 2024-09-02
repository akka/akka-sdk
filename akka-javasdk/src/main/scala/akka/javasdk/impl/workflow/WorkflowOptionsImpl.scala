/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.annotation.InternalApi
import akka.javasdk.workflow.WorkflowOptions

import java.util
import java.util.Collections

/**
 * INTERNAL API
 */
@InternalApi
case class WorkflowOptionsImpl(override val forwardHeaders: java.util.Set[String]) extends WorkflowOptions {

  /**
   * Ask the runtime to forward these headers from the incoming request as metadata headers for the incoming commands.
   * By default, no headers except "X-Server-Timing" are forwarded.
   */
  override def withForwardHeaders(headers: util.Set[String]): WorkflowOptions =
    copy(forwardHeaders = Collections.unmodifiableSet(new util.HashSet(headers)))

}

/**
 * INTERNAL API
 */
@InternalApi
object WorkflowOptionsImpl {
  val defaults = new WorkflowOptionsImpl(Collections.emptySet())
}
