/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.evaluation;

import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
import java.util.concurrent.CompletableFuture;

/**
 * A simple evaluator used in unit tests. Branches on the subject's interaction id to exercise the
 * complete, inconclusive, and async effects.
 */
public class SimpleEvaluator extends Evaluator {

  @Override
  public Effect evaluate(EvaluationContext context) {
    Subject subject = context.subject();
    String interactionId =
        switch (subject) {
          case Subject.Interaction i -> i.interactionId();
          case Subject.Flow f -> f.flowId();
          case Subject.Session s -> s.sessionId();
          case Subject.EvaluatedEvaluation e -> e.evaluationId();
          case Subject.Experiment x -> x.experimentId();
        };
    String agentComponentId =
        subject instanceof Subject.Interaction i ? i.agentComponentId().orElse(null) : null;

    return switch (interactionId) {
      case "inconclusive" -> effects().inconclusive("cannot evaluate interaction " + interactionId);
      case "async" ->
          // delegate asynchronously, resolving to a completed evaluation
          effects()
              .asyncEffect(
                  CompletableFuture.completedFuture(
                      effects().complete(Evaluation.passed("async verdict").withScore(0.5))));
      default ->
          effects()
              .complete(
                  Evaluation.passed("evaluated for " + context.evaluationId())
                      .withScore(0.9)
                      .withLabel("good")
                      .withAttribute("agent", agentComponentId));
    };
  }
}
