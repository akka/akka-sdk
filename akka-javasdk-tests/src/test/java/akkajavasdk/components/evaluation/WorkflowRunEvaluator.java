/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An evaluator of workflow runs, started on request by {@link EvaluatedWorkflow}. It is bound to no
 * agent. The verdict names the run and the question passed as a trigger attribute, and carries the
 * attributes of the trigger. The evaluator counts its runs per workflow id.
 */
@Component(id = "workflow-run-evaluator")
public class WorkflowRunEvaluator extends Evaluator {

  private static final ConcurrentHashMap<String, AtomicInteger> runs = new ConcurrentHashMap<>();

  /** How many times the evaluator ran for the given workflow id. */
  public static int runsFor(String workflowId) {
    var count = runs.get(workflowId);
    return count == null ? 0 : count.get();
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    return switch (context.subject()) {
      case Subject.Workflow workflow -> {
        runs.computeIfAbsent(workflow.workflowId(), id -> new AtomicInteger()).incrementAndGet();
        yield effects()
            .complete(
                Evaluation.passed(
                        "run "
                            + workflow.workflowId()
                            + " answered "
                            + context.attributes().get("question"))
                    .withAttributes(context.attributes()));
      }
      case Subject.Interaction interaction ->
          effects().notApplicable("only workflow runs are evaluated");
    };
  }
}
