/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracecontext;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import java.util.concurrent.Executor;

@Component(id = "trace-context-workflow")
public class TraceContextWorkflow extends Workflow<TraceContextWorkflow.State> {

  public record State(SpanIds inCommand, SpanIds inStep) {}

  private final Executor executor;

  public TraceContextWorkflow(Executor executor) {
    this.executor = executor;
  }

  public Effect<String> start() {
    var inCommand = SpanIds.observe(commandContext().tracing(), executor);
    return effects()
        .updateState(new State(inCommand, null))
        .transitionTo(TraceContextWorkflow::observeStep)
        .thenReply("ok");
  }

  public StepEffect observeStep() {
    var inStep = SpanIds.observe(commandContext().tracing(), executor);
    return stepEffects().updateState(new State(currentState().inCommand(), inStep)).thenEnd();
  }

  public Effect<State> get() {
    return effects().reply(currentState());
  }
}
