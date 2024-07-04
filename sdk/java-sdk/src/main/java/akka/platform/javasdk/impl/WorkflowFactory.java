/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl;

import akka.platform.javasdk.impl.workflow.WorkflowRouter;
import akka.platform.javasdk.workflow.WorkflowContext;

public interface WorkflowFactory {

  WorkflowRouter<?, ?> create(WorkflowContext context);
}
