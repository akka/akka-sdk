/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.annotation.InternalApi;
import akka.javasdk.impl.workflow.WorkflowRouter;
import akka.javasdk.workflow.WorkflowContext;

/** INTERNAL API */
@InternalApi
public interface WorkflowFactory {

  WorkflowRouter<?, ?> create(WorkflowContext context);
}
