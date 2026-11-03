/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.impl.ErrorHandling;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Client for starting an evaluation by an {@link akka.javasdk.evaluation.Evaluator} or {@link
 * akka.javasdk.evaluation.DurableEvaluator} on request, outside the bindings of the evaluator to
 * agents. The final step of a workflow uses it to have the outcome of the whole run evaluated, and
 * an endpoint uses it to evaluate a recorded interaction again.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface EvaluatorClient {

  /**
   * Start an evaluation of the subject by this evaluator. The evaluation runs in the background.
   * Its outcome is recorded in the ledger under the returned evaluation id, see {@link
   * akka.javasdk.ledger.LedgerClient#getEvaluation(String)}.
   *
   * <p>The evaluation id is derived from the evaluator, the subject and the attributes, so a
   * repeated call with the same arguments, for example from a retried workflow step, starts the
   * evaluation once.
   *
   * <p>Blocks the calling thread until the evaluation is durably queued. Safe to call on a virtual
   * thread. Use {@link #startEvaluationAsync(Subject, Map)} for the non-blocking variant.
   *
   * @param subject what to evaluate
   * @param attributes pointers for the evaluator, available as {@link
   *     akka.javasdk.evaluation.EvaluationContext#attributes()}, for example the ids of the
   *     interactions to judge; the attributes are recorded with the evaluation
   * @return the evaluation id
   * @throws IllegalArgumentException if no evaluator has the component id of this client
   * @throws IllegalStateException if evaluators or the ledger are disabled
   */
  default String startEvaluation(Subject subject, Map<String, String> attributes) {
    try {
      return startEvaluationAsync(subject, attributes).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /**
   * Async variant of {@link #startEvaluation(Subject, Map)}.
   *
   * @param subject what to evaluate
   * @param attributes pointers for the evaluator, recorded with the evaluation
   * @return a stage that completes with the evaluation id once the evaluation is durably queued, or
   *     fails with {@link IllegalArgumentException} if no evaluator has the component id of this
   *     client, or with {@link IllegalStateException} if evaluators or the ledger are disabled
   */
  CompletionStage<String> startEvaluationAsync(Subject subject, Map<String, String> attributes);
}
