/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import java.util.Map;

/**
 * A workflow whose final step starts an evaluation of its own run by {@link WorkflowRunEvaluator},
 * with the question of the run as a trigger attribute, and keeps the evaluation id in its state.
 */
@Component(id = "evaluated-workflow")
public class EvaluatedWorkflow extends Workflow<EvaluatedWorkflow.State> {

  public record State(String question, String evaluationId) {}

  private final WorkflowContext context;
  private final ComponentClient componentClient;

  public EvaluatedWorkflow(WorkflowContext context, ComponentClient componentClient) {
    this.context = context;
    this.componentClient = componentClient;
  }

  public Effect<Done> run(String question) {
    return effects()
        .updateState(new State(question, null))
        .transitionTo(EvaluatedWorkflow::evaluate)
        .thenReply(Done.getInstance());
  }

  public StepEffect evaluate() {
    var evaluationId =
        componentClient
            .forEvaluator(WorkflowRunEvaluator.class)
            .startEvaluation(
                new Subject.Workflow(context.workflowId(), "evaluated-workflow"),
                Map.of("question", currentState().question()));
    return stepEffects().updateState(new State(currentState().question(), evaluationId)).thenEnd();
  }

  public Effect<State> get() {
    return effects().reply(currentState());
  }
}
