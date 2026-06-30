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
 * A simple evaluator used in unit tests. Branches on the subject's sequence number to exercise the
 * record, error, and async effects.
 */
public class SimpleEvaluator extends Evaluator {

  @Override
  public Effect evaluate(EvaluationContext context) {
    Subject subject = context.subject();
    long sequenceNr = subject.sequenceNr();

    if (sequenceNr < 0) {
      return effects().error("cannot evaluate interaction " + sequenceNr);
    } else if (sequenceNr == 0) {
      // delegate asynchronously, resolving to a recorded evaluation
      return effects()
          .asyncEffect(
              CompletableFuture.completedFuture(
                  effects().record(Evaluation.passed("async verdict").withScore(0.5))));
    } else {
      return effects()
          .record(
              Evaluation.passed("evaluated in session " + context.evaluationSession())
                  .withScore(0.9)
                  .withLabel("good")
                  .withAttribute("agent", subject.agentComponentId()));
    }
  }
}
