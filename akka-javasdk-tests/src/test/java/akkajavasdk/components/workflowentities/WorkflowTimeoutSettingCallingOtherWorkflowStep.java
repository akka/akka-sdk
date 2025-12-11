/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import java.time.Duration;

@Component(id = "workflow-timeout-setting-calling-other-workflow")
public class WorkflowTimeoutSettingCallingOtherWorkflowStep extends Workflow<String> {

  @Override
  public String emptyState() {
    return "";
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .timeout(Duration.ofSeconds(10), WorkflowWithTimeout::counterStep)
        .build();
  }

  public Effect<Done> start() {
    return effects().updateState("123").end().thenReply(done());
  }
}
