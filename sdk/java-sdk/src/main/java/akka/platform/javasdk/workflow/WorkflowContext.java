/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.workflow;

import akka.platform.javasdk.Context;

public interface WorkflowContext extends Context {
  /**
   * The id of the workflow that this context is for.
   *
   * @return The workflow id.
   */
  String workflowId();
}
