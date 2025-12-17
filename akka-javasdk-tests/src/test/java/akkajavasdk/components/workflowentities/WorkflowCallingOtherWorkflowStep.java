/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "workflow-calling-other-workflow-step")
public class WorkflowCallingOtherWorkflowStep extends Workflow<String> {

  @Override
  public String emptyState() {
    return "";
  }

  public Effect<Done> start() {
    return effects()
        .updateState("123")
        .transitionTo(WorkflowWithTimeout::counterStep)
        .thenReply(done());
  }
}
