/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.evaluation;

import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;

/**
 * A simple evaluator used in unit tests. Branches on the subject's interaction id to exercise the
 * complete and inconclusive effects.
 */
public class SimpleEvaluator extends Evaluator {

  @Override
  public Effect evaluate(EvaluationContext context) {
    return switch (context.subject()) {
      case Subject.Interaction interaction -> evaluateInteraction(context, interaction);
      case Subject.Workflow workflow ->
          effects()
              .complete(
                  Evaluation.passed("evaluated run " + workflow.workflowId())
                      .withAttributes(context.attributes()));
    };
  }

  private Effect evaluateInteraction(EvaluationContext context, Subject.Interaction subject) {
    return switch (subject.interactionId()) {
      case "inconclusive" ->
          effects().inconclusive("cannot evaluate interaction " + subject.interactionId());
      default ->
          effects()
              .complete(
                  Evaluation.passed("evaluated for " + context.evaluationId())
                      .withScore(0.9)
                      .withLabel("good")
                      .withAttribute("agent", subject.agentComponentId()));
    };
  }
}
