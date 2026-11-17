/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracing;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;

@Component(id = "span-recording-workflow")
public class SpanRecordingWorkflow extends Workflow<String> {

  private final WorkflowContext workflowContext;

  public SpanRecordingWorkflow(WorkflowContext workflowContext) {
    this.workflowContext = workflowContext;
  }

  public Effect<Done> start() {
    return effects().transitionTo(SpanRecordingWorkflow::recordStep).thenReply(Done.done());
  }

  private StepEffect recordStep() {
    SpanRecorder.record("workflow:" + workflowContext.workflowId());
    return stepEffects().thenEnd();
  }
}
