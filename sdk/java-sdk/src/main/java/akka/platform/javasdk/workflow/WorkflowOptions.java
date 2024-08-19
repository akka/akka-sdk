/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.workflow;

import akka.platform.javasdk.impl.CallableComponentOptions;
import akka.platform.javasdk.impl.workflow.WorkflowOptionsImpl;

public interface WorkflowOptions extends CallableComponentOptions {

  static WorkflowOptions defaults() {
    return WorkflowOptionsImpl.defaults();
  }
}
