/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;

/**
 * An evaluator of workflow runs, started on request by {@link EvaluatedWorkflow}. It is bound to no
 * agent. The verdict names the run and the question passed as a trigger attribute, and carries the
 * attributes of the trigger.
 */
@Component(id = "workflow-run-evaluator")
public class WorkflowRunEvaluator extends Evaluator {

  @Override
  public Effect evaluate(EvaluationContext context) {
    return switch (context.subject()) {
      case Subject.Workflow workflow ->
          effects()
              .complete(
                  Evaluation.passed(
                          "run "
                              + workflow.workflowId()
                              + " answered "
                              + context.attributes().get("question"))
                      .withAttributes(context.attributes()));
      case Subject.Interaction interaction ->
          effects().notApplicable("only workflow runs are evaluated");
    };
  }
}
