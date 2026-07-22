/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory {@link LedgerClient} for unit tests.
 *
 * <p>Seed it with {@link InteractionRecord}s via {@link #seed(InteractionRecord)}, or with {@link
 * EvaluationRecord}s via {@link #seed(EvaluationRecord)}, and pass it to the component under test
 * (for example an {@link akka.javasdk.evaluation.Evaluator} constructed by an {@link
 * EvaluatorTestKit}) to assert it fetches and uses them, with no database.
 */
public final class TestLedgerClient implements LedgerClient {

  private final ConcurrentMap<String, InteractionRecord> records = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, EvaluationRecord> evaluations = new ConcurrentHashMap<>();

  /** Create an empty in-memory ledger client. */
  public static TestLedgerClient create() {
    return new TestLedgerClient();
  }

  /**
   * Seed a record, keyed by its {@link InteractionRecord#interactionId()}. Replaces any existing
   * record with the same id.
   *
   * @param record the record to seed
   * @return this client, for chaining
   */
  public TestLedgerClient seed(InteractionRecord record) {
    records.put(record.interactionId(), record);
    return this;
  }

  @Override
  public InteractionRecord getInteraction(String interactionId) {
    var record = records.get(interactionId);
    if (record == null) {
      throw new NoSuchElementException("No interaction with id [" + interactionId + "]");
    }
    return record;
  }

  @Override
  public CompletionStage<InteractionRecord> getInteractionAsync(String interactionId) {
    var record = records.get(interactionId);
    if (record == null) {
      return CompletableFuture.failedFuture(
          new NoSuchElementException("No interaction with id [" + interactionId + "]"));
    }
    return CompletableFuture.completedFuture(record);
  }

  /**
   * Seed an evaluation record, keyed by its {@link EvaluationRecord#evaluationId()}. Replaces any
   * existing record with the same id.
   *
   * @param record the record to seed
   * @return this client, for chaining
   */
  public TestLedgerClient seed(EvaluationRecord record) {
    evaluations.put(record.evaluationId(), record);
    return this;
  }

  @Override
  public EvaluationRecord getEvaluation(String evaluationId) {
    var record = evaluations.get(evaluationId);
    if (record == null) {
      throw new NoSuchElementException("No evaluation with id [" + evaluationId + "]");
    }
    return record;
  }

  @Override
  public CompletionStage<EvaluationRecord> getEvaluationAsync(String evaluationId) {
    var record = evaluations.get(evaluationId);
    if (record == null) {
      return CompletableFuture.failedFuture(
          new NoSuchElementException("No evaluation with id [" + evaluationId + "]"));
    }
    return CompletableFuture.completedFuture(record);
  }
}
