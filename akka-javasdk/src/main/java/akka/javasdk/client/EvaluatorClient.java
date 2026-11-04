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
 * Client for starting evaluations on request with an {@link akka.javasdk.evaluation.Evaluator} or
 * {@link akka.javasdk.evaluation.DurableEvaluator}, outside automatic agent bindings.
 *
 * <p>Typically called from the final step of a workflow to evaluate the outcome of an entire run,
 * or from an endpoint to evaluate a recorded interaction again.
 *
 * <p>The evaluation ID is deterministically derived from the evaluator, the subject, and the
 * attributes. Repeated calls with identical arguments return the same evaluation ID:
 *
 * <ul>
 *   <li>For an {@code Evaluator}, each repeated call triggers another evaluation and records each
 *       run under that ID. Retried workflow steps may therefore run the evaluator multiple times.
 *   <li>For a {@code DurableEvaluator}, the evaluation runs only once per ID while the instance
 *       exists. Subsequent calls return the existing ID without re-executing.
 * </ul>
 *
 * To evaluate the same subject again intentionally (for instance, after updating a prompt), pass
 * distinct attributes, such as a prompt version.
 *
 * <p>Not for user extension; implementation is provided by the SDK.
 */
@DoNotInherit
public interface EvaluatorClient {

  /**
   * Starts an evaluation of the given subject.
   *
   * <p>The evaluation runs asynchronously in the background. Its outcome is recorded in the ledger
   * under the returned evaluation ID; see {@link
   * akka.javasdk.ledger.LedgerClient#getEvaluation(String)}.
   *
   * <p>Blocks the calling thread until the evaluation is durably queued. Safe to call on a virtual
   * thread. Use {@link #startEvaluationAsync(Subject, Map)} for non-blocking execution.
   *
   * @param subject what to evaluate; must not be null
   * @param attributes contextual metadata passed to {@link
   *     akka.javasdk.evaluation.EvaluationContext#attributes()}, such as interaction IDs to judge;
   *     recorded with the evaluation; must not be null
   * @return the generated evaluation ID
   * @throws NullPointerException if {@code subject} or {@code attributes} is null
   * @throws IllegalArgumentException if no evaluator matches this client component ID, if the
   *     subject has an empty or oversized ID (> 256 characters), if an attribute key is empty, or
   *     if total attribute keys and values exceed 8192 characters
   * @throws IllegalStateException if evaluators or the ledger are disabled, or if this evaluator
   *     has {@code enabled = false} in its configuration
   */
  default String startEvaluation(Subject subject, Map<String, String> attributes) {
    try {
      return startEvaluationAsync(subject, attributes).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /**
   * Asynchronously starts an evaluation of the given subject.
   *
   * @param subject what to evaluate; must not be null
   * @param attributes contextual metadata passed to the evaluator; must not be null
   * @return a completion stage that completes with the evaluation ID once durably queued, or fails
   *     with the exceptions documented in {@link #startEvaluation(Subject, Map)}
   */
  CompletionStage<String> startEvaluationAsync(Subject subject, Map<String, String> attributes);
}
