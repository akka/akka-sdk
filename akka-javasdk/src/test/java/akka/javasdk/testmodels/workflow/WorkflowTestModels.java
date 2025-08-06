/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.workflow;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.StepName;
import akka.javasdk.annotations.JWT;
import akka.javasdk.workflow.Workflow;

public class WorkflowTestModels {

  @ComponentId("transfer-workflow")
  public static class TransferWorkflow extends Workflow<WorkflowState> {

    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }

    public StepEffect depositStep() {
      return null;
    }

    @StepName("withdraw")
    public StepEffect withdrawStep() {
      return null;
    }

    public Effect<WorkflowState> getState() {
      return null;
    }
  }
}
