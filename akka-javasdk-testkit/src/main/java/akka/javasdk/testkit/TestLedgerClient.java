/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.ledger.ActivityRecord;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.FlowPage;
import akka.javasdk.ledger.FlowRecord;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * An in-memory {@link LedgerClient} for unit tests.
 *
 * <p>Seed it with {@link InteractionRecord}s via {@link #seed(InteractionRecord)}, with {@link
 * EvaluationRecord}s via {@link #seed(EvaluationRecord)}, with {@link GuardrailDecisionRecord}s via
 * {@link #seed(GuardrailDecisionRecord)}, or with {@link ActivityRecord}s via {@link
 * #seed(ActivityRecord)}, and pass it to the component under test (for example an {@link
 * akka.javasdk.evaluation.Evaluator} constructed by an {@link EvaluatorTestKit}) to assert it
 * fetches and uses them, with no database.
 *
 * <p>The records of a flow, as {@link #getFlowRecords(String)} returns them, are the seeded
 * interactions, guardrail decisions and activity records with that flow id, and the seeded
 * evaluations of the seeded interactions with that flow id. They come oldest timestamp first, and
 * records with the same timestamp come in the order they were seeded. A position is the 1-based
 * index of a record in that order. A page holds at most 100 records.
 */
public final class TestLedgerClient implements LedgerClient {

  private static final int FLOW_PAGE_LIMIT = 100;

  private final ConcurrentMap<String, InteractionRecord> records = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, EvaluationRecord> evaluations = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, GuardrailDecisionRecord> guardrailDecisions =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ActivityRecord> activities = new ConcurrentHashMap<>();

  // The order in which the records were seeded, which orders the records of a flow with the same
  // timestamp. A record that is seeded again takes its new place. A seed writes the order before
  // the record, so a flow read that runs during a seed finds the order of every record it reads.
  // Replaced records stay in the map.
  private final AtomicLong seedCount = new AtomicLong();
  private final Map<FlowRecord, Long> seedOrder =
      Collections.synchronizedMap(new IdentityHashMap<>());

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
    seedOrder.put(record, seedCount.getAndIncrement());
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
    seedOrder.put(record, seedCount.getAndIncrement());
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

  @Override
  public List<EvaluationRecord> getEvaluations(String interactionId) {
    return evaluations.values().stream()
        .filter(
            record ->
                record.subject() instanceof EvaluationRecord.Subject.Interaction interaction
                    && interaction.interactionId().equals(interactionId))
        .sorted(Comparator.comparing(EvaluationRecord::timestamp).reversed())
        .toList();
  }

  @Override
  public CompletionStage<List<EvaluationRecord>> getEvaluationsAsync(String interactionId) {
    return CompletableFuture.completedFuture(getEvaluations(interactionId));
  }

  @Override
  public List<EvaluationRecord> getWorkflowEvaluations(
      String workflowComponentId, String workflowId) {
    return evaluations.values().stream()
        .filter(
            record ->
                record.subject() instanceof EvaluationRecord.Subject.Workflow workflow
                    && workflow.workflowComponentId().equals(workflowComponentId)
                    && workflow.workflowId().equals(workflowId))
        .sorted(Comparator.comparing(EvaluationRecord::timestamp).reversed())
        .toList();
  }

  @Override
  public CompletionStage<List<EvaluationRecord>> getWorkflowEvaluationsAsync(
      String workflowComponentId, String workflowId) {
    return CompletableFuture.completedFuture(
        getWorkflowEvaluations(workflowComponentId, workflowId));
  }

  @Override
  public List<InteractionRecord> getInteractions(String agentComponentId, String sessionId) {
    return records.values().stream()
        .filter(
            record ->
                record.agentComponentId().equals(agentComponentId)
                    && record.sessionId().equals(sessionId))
        .sorted(Comparator.comparing(InteractionRecord::timestamp).reversed())
        .toList();
  }

  @Override
  public CompletionStage<List<InteractionRecord>> getInteractionsAsync(
      String agentComponentId, String sessionId) {
    return CompletableFuture.completedFuture(getInteractions(agentComponentId, sessionId));
  }

  /**
   * Seed a guardrail decision record, keyed by its {@link GuardrailDecisionRecord#recordId()}.
   * Replaces any existing record with the same id.
   *
   * @param record the record to seed
   * @return this client, for chaining
   */
  public TestLedgerClient seed(GuardrailDecisionRecord record) {
    seedOrder.put(record, seedCount.getAndIncrement());
    guardrailDecisions.put(record.recordId(), record);
    return this;
  }

  @Override
  public List<GuardrailDecisionRecord> getGuardrailDecisions(String interactionId) {
    return guardrailDecisions.values().stream()
        .filter(record -> record.interactionId().equals(interactionId))
        .sorted(Comparator.comparing(GuardrailDecisionRecord::timestamp).reversed())
        .toList();
  }

  @Override
  public CompletionStage<List<GuardrailDecisionRecord>> getGuardrailDecisionsAsync(
      String interactionId) {
    return CompletableFuture.completedFuture(getGuardrailDecisions(interactionId));
  }

  /**
   * Seed an activity record, keyed by its {@link ActivityRecord#recordId()}. Replaces any existing
   * record with the same id.
   *
   * @param record the record to seed
   * @return this client, for chaining
   */
  public TestLedgerClient seed(ActivityRecord record) {
    seedOrder.put(record, seedCount.getAndIncrement());
    activities.put(record.recordId(), record);
    return this;
  }

  @Override
  public FlowPage getFlowRecords(String flowId) {
    return getFlowRecords(flowId, 0);
  }

  @Override
  public FlowPage getFlowRecords(String flowId, long fromPosition) {
    if (flowId.isEmpty()) {
      throw new IllegalArgumentException("The flow id must not be empty");
    }
    var inFlow = Optional.of(flowId);
    var interactions =
        records.values().stream().filter(record -> record.flowId().equals(inFlow)).toList();
    var interactionIds =
        interactions.stream().map(InteractionRecord::interactionId).collect(Collectors.toSet());

    var flowRecords = new ArrayList<FlowRecord>(interactions);
    guardrailDecisions.values().stream()
        .filter(record -> record.flowId().equals(inFlow))
        .forEach(flowRecords::add);
    activities.values().stream()
        .filter(record -> record.flowId().equals(flowId))
        .forEach(flowRecords::add);
    evaluations.values().stream()
        .filter(record -> interactionIds.contains(record.interactionId()))
        .forEach(flowRecords::add);
    flowRecords.sort(Comparator.comparing(FlowRecord::timestamp).thenComparingLong(seedOrder::get));

    // position 0 reads from the start, as position 1 does
    var from = (int) Math.min(Math.max(fromPosition, 1) - 1, flowRecords.size());
    var to = Math.min(from + FLOW_PAGE_LIMIT, flowRecords.size());
    var nextPosition = to < flowRecords.size() ? Optional.of(to + 1L) : Optional.<Long>empty();
    return new FlowPage(List.copyOf(flowRecords.subList(from, to)), nextPosition);
  }

  @Override
  public CompletionStage<FlowPage> getFlowRecordsAsync(String flowId) {
    return getFlowRecordsAsync(flowId, 0);
  }

  @Override
  public CompletionStage<FlowPage> getFlowRecordsAsync(String flowId, long fromPosition) {
    try {
      return CompletableFuture.completedFuture(getFlowRecords(flowId, fromPosition));
    } catch (IllegalArgumentException e) {
      return CompletableFuture.failedFuture(e);
    }
  }
}
