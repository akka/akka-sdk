/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.workflow;

import akka.javasdk.Context;
import akka.javasdk.Tracing;

public interface WorkflowContext extends Context {
  /**
   * The id of the workflow that this context is for.
   *
   * @return The workflow id.
   */
  String workflowId();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();
}
