/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.workflow;

import akka.javasdk.impl.CallableComponentOptions;
import akka.javasdk.impl.workflow.WorkflowOptionsImpl;

public interface WorkflowOptions extends CallableComponentOptions {

  static WorkflowOptions defaults() {
    return WorkflowOptionsImpl.defaults();
  }
}
