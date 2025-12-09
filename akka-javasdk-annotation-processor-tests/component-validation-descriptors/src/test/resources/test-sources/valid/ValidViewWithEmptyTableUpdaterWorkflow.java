/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akka.javasdk.workflow.Workflow;

@Component(id = "view-with-empty-table-updater-workflow")
public class ValidViewWithEmptyTableUpdaterWorkflow extends View {

  public static class WorkflowState {
    public String workflowId;
    public String status;
  }

  @Query("SELECT * FROM workflows")
  public QueryEffect<WorkflowState> getWorkflows() {
    return null;
  }

  // Empty TableUpdater - passthrough scenario where state type matches row type
  // This is valid for Workflow subscriptions when no transformation is needed
  // The MyWorkflow has WorkflowState as its state type, which matches the row type
  @Consume.FromWorkflow(MyWorkflow.class)
  public static class Workflows extends TableUpdater<WorkflowState> {}

  public static class MyWorkflow extends Workflow<WorkflowState> {
    @Override
    public WorkflowDef<WorkflowState> definition() {
      return null;
    }
  }
}
