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
 * akka.javasdk.evaluation.Evaluator} is the primary consumer: it fetches the interaction referenced
 * by its {@link akka.javasdk.evaluation.Subject#interactionId()} to evaluate it.
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
}
