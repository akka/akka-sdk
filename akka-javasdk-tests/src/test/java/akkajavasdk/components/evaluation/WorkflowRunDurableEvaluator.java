/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static java.time.Duration.ofSeconds;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.DurableEvaluator;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Subject;

/**
 * A durable evaluator of workflow runs, started on request through the component client. It is
 * bound to no agent. One step reads the run from the subject and the trigger attributes, and the
 * next step records the verdict with the attributes of the trigger.
 */
@Component(id = "workflow-run-durable-evaluator")
public class WorkflowRunDurableEvaluator
    extends DurableEvaluator<WorkflowRunDurableEvaluator.State> {

  public record State(String summary) {}

  @Override
  public Settings settings() {
    return Settings.defaults()
        .withEvaluationTimeout(ofSeconds(10))
        .withDefaultStepTimeout(ofSeconds(5))
        .withMaxStepRetries(2);
  }

  @Override
  public Effect onEvaluation(EvaluationContext context) {
    if (!(context.subject() instanceof Subject.Workflow)) {
      return effects().notApplicable("only workflow runs are evaluated");
    }
    return effects().transitionTo(WorkflowRunDurableEvaluator::summarize);
  }

  private Effect summarize() {
    var run = (Subject.Workflow) evaluationContext().subject();
    var summary =
        "run "
            + run.workflowId()
            + " of "
            + run.workflowComponentId()
            + " answered "
            + evaluationContext().attributes().get("question");
    return effects()
        .updateState(new State(summary))
        .transitionTo(WorkflowRunDurableEvaluator::judge);
  }

  private Effect judge() {
    return effects()
        .complete(
            Evaluation.passed(currentState().summary())
                .withAttributes(evaluationContext().attributes()));
  }
}
