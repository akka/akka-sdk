/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

/**
 * Client for fetching records from the ledger — the log of recorded agent interactions.
 *
 * <p>A {@code LedgerClient} can be injected into any component. An {@link
 * akka.javasdk.evaluation.Evaluator} evaluating an {@link
 * akka.javasdk.evaluation.Subject.Interaction} does not need to call it directly — the runtime
 * resolves that interaction's content onto {@link
 * akka.javasdk.evaluation.EvaluationContext#interaction()} before the evaluator runs. It remains
 * useful for fetching ledger detail beyond the subject, such as a prior evaluation record.
 *
 * <p>Not for user extension.
 */
public interface LedgerClient {

  /**
   * Fetch the full record for the interaction with the given (globally unique) {@code
   * interactionId}.
   *
   * <p>Blocks the calling thread until the record has been fetched. Safe to call on a Loom virtual
   * thread. Use {@link #getInteractionAsync(String)} for the non-blocking variant.
   *
   * @param interactionId the globally unique id of the interaction to fetch
   * @return the interaction record
   * @throws NoSuchElementException if no interaction exists with that id
   */
  InteractionRecord getInteraction(String interactionId);

  /**
   * Async variant of {@link #getInteraction(String)}.
   *
   * @param interactionId the globally unique id of the interaction to fetch
   * @return a stage that completes with the interaction record, or fails with {@link
   *     NoSuchElementException} if no interaction exists with that id
   */
  CompletionStage<InteractionRecord> getInteractionAsync(String interactionId);

  /**
   * Fetch the record for the evaluation with the given {@code evaluationId}. An evaluation is
   * recorded when it terminates, so the record exists only once the evaluation has finished.
   *
   * <p>Blocks the calling thread until the record has been fetched. Safe to call on a Loom virtual
   * thread. Use {@link #getEvaluationAsync(String)} for the non-blocking variant.
   *
   * @param evaluationId the globally unique id of the evaluation to fetch
   * @return the evaluation record
   * @throws NoSuchElementException if no evaluation exists with that id
   */
  EvaluationRecord getEvaluation(String evaluationId);

  /**
   * Async variant of {@link #getEvaluation(String)}.
   *
   * @param evaluationId the globally unique id of the evaluation to fetch
   * @return a stage that completes with the evaluation record, or fails with {@link
   *     NoSuchElementException} if no evaluation exists with that id
   */
  CompletionStage<EvaluationRecord> getEvaluationAsync(String evaluationId);
}
