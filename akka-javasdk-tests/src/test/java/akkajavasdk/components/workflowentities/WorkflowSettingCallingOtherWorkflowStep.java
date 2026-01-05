/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "workflow-setting-calling-other-workflow")
public class WorkflowSettingCallingOtherWorkflowStep extends Workflow<String> {

  @Override
  public String emptyState() {
    return "";
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .stepRecovery(
            WorkflowWithTimeout::counterStep,
            maxRetries(10).failoverTo(WorkflowSettingCallingOtherWorkflowStep::someStep))
        .build();
  }

  StepEffect someStep() {
    return stepEffects().thenEnd();
  }

  public Effect<Done> start() {
    return effects().updateState("123").end().thenReply(done());
  }
}
