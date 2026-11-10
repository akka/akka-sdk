/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import akka.annotation.DoNotInherit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

/**
 * Client for fetching records from the ledger, the log of recorded agent interactions, their
 * evaluations, the guardrail decisions made in them, the classifications produced in them and the
 * activity of autonomous agents.
 *
 * <p>A {@code LedgerClient} can be injected into any component. An {@link
 * akka.javasdk.evaluation.Evaluator} uses it to fetch the interactions that its {@link
 * akka.javasdk.evaluation.Subject} and {@link
 * akka.javasdk.evaluation.EvaluationContext#attributes()} refer to. When an interaction ran in a
 * flow, the evaluator reads the records of the flow with {@link #getFlowRecords(String)}.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface LedgerClient {

  /**
   * Fetch the full record for the interaction with the given (globally unique) {@code
   * interactionId}.
   *
   * <p>Blocks the calling thread until the record has been fetched. Safe to call on a virtual
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
   * <p>Blocks the calling thread until the record has been fetched. Safe to call on a virtual
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

  /**
   * Fetch the record for the classification with the given {@code classificationId}.
   *
   * <p>Blocks the calling thread until the record has been fetched. Safe to call on a virtual
   * thread. Use {@link #getClassificationAsync(String)} for the non-blocking variant.
   *
   * @param classificationId the globally unique id of the classification to fetch
   * @return the classification record
   * @throws NoSuchElementException if no classification exists with that id
   */
  ClassificationRecord getClassification(String classificationId);

  /**
   * Async variant of {@link #getClassification(String)}.
   *
   * @param classificationId the globally unique id of the classification to fetch
   * @return a stage that completes with the classification record, or fails with {@link
   *     NoSuchElementException} if no classification exists with that id
   */
  CompletionStage<ClassificationRecord> getClassificationAsync(String classificationId);

  /**
   * Fetch the evaluations recorded about the interaction with the given {@code interactionId},
   * newest first.
   *
   * <p>Blocks the calling thread until the records have been fetched. Safe to call on a virtual
   * thread. Use {@link #getEvaluationsAsync(String)} for the non-blocking variant.
   *
   * @param interactionId the id of the evaluated interaction, as carried by {@link
   *     akka.javasdk.agent.Agent.AgentReply#interactionId()}
   * @return the evaluations of that interaction, or an empty list when none are recorded
   */
  List<EvaluationRecord> getEvaluations(String interactionId);

  /**
   * Async variant of {@link #getEvaluations(String)}.
   *
   * @param interactionId the id of the evaluated interaction
   * @return a stage that completes with the evaluations of that interaction
   */
  CompletionStage<List<EvaluationRecord>> getEvaluationsAsync(String interactionId);

  /**
   * Fetches recorded evaluations for a workflow run, sorted newest first. Returns at most 100
   * records.
   *
   * <p>Blocks the calling thread until the records have been fetched. Safe to call on a virtual
   * thread. Use {@link #getWorkflowEvaluationsAsync(String, String)} for non-blocking execution.
   *
   * @param workflowComponentId the component ID of the workflow
   * @param workflowId the ID of the evaluated workflow instance
   * @return evaluations of the workflow run, or an empty list if none are recorded
   */
  List<EvaluationRecord> getWorkflowEvaluations(String workflowComponentId, String workflowId);

  /**
   * Asynchronously fetches recorded evaluations for a workflow run, sorted newest first.
   *
   * @param workflowComponentId the component ID of the workflow
   * @param workflowId the ID of the evaluated workflow instance
   * @return a stage completing with evaluations of the workflow run, or an empty list if none
   */
  CompletionStage<List<EvaluationRecord>> getWorkflowEvaluationsAsync(
      String workflowComponentId, String workflowId);

  /**
   * Fetch the interactions of the agent with the given {@code agentComponentId} in the session with
   * the given {@code sessionId}, newest first. At most the 100 newest interactions are returned.
   *
   * <p>Blocks the calling thread until the records have been fetched. Safe to call on a virtual
   * thread. Use {@link #getInteractionsAsync(String, String)} for the non-blocking variant.
   *
   * @param agentComponentId the component id of the agent
   * @param sessionId the session the interactions belong to
   * @return the newest interactions of the agent in that session, or an empty list when none are
   *     recorded
   */
  List<InteractionRecord> getInteractions(String agentComponentId, String sessionId);

  /**
   * Async variant of {@link #getInteractions(String, String)}.
   *
   * @param agentComponentId the component id of the agent
   * @param sessionId the session the interactions belong to
   * @return a stage that completes with the interactions of the agent in that session
   */
  CompletionStage<List<InteractionRecord>> getInteractionsAsync(
      String agentComponentId, String sessionId);

  /**
   * Fetch the guardrail decisions recorded during the interaction with the given {@code
   * interactionId}, newest first.
   *
   * <p>Blocks the calling thread until the records have been fetched. Safe to call on a virtual
   * thread. Use {@link #getGuardrailDecisionsAsync(String)} for the non-blocking variant.
   *
   * @param interactionId the id of the interaction the guardrails ran in
   * @return the guardrail decisions of that interaction, or an empty list when none are recorded
   */
  List<GuardrailDecisionRecord> getGuardrailDecisions(String interactionId);

  /**
   * Async variant of {@link #getGuardrailDecisions(String)}.
   *
   * @param interactionId the id of the interaction the guardrails ran in
   * @return a stage that completes with the guardrail decisions of that interaction
   */
  CompletionStage<List<GuardrailDecisionRecord>> getGuardrailDecisionsAsync(String interactionId);

  /**
   * Read the first page of the records of the flow with the given {@code flowId}, in the order they
   * were recorded. The records are the {@link InteractionRecord}s of the agents in the flow, the
   * {@link ActivityRecord}s of its autonomous agents, the {@link GuardrailDecisionRecord}s of its
   * interactions and the {@link EvaluationRecord}s of its interactions. No other kind of record is
   * returned. A page holds at most 100 records.
   *
   * <p>The client leaves two kinds of record out of a page: a record of a class that this version
   * of the SDK does not know, and an evaluation of a subject that is not an interaction, such as a
   * workflow run. A page can then hold fewer than 100 records, or none, while {@link
   * FlowPage#hasMore()} is {@code true}. Use {@code hasMore}, not the number of records, to decide
   * whether to read the next page.
   *
   * <p>Get the flow id of an interaction from {@link
   * akka.javasdk.evaluation.Subject.Interaction#flowId()} or {@link InteractionRecord#flowId()}. To
   * read the next page, pass the {@link FlowPage#nextPosition()} of the page to {@link
   * #getFlowRecords(String, long)}. A page where {@link FlowPage#hasMore()} is {@code false} was
   * the last page when it was read. To read the records that were recorded after the last page,
   * pass its next position later.
   *
   * <p>Blocks the calling thread until the records have been fetched. Safe to call on a virtual
   * thread. Use {@link #getFlowRecordsAsync(String)} for the non-blocking variant.
   *
   * @param flowId the id of the flow
   * @return the first page of the records of the flow, or an empty page for an unknown flow
   * @throws IllegalArgumentException if the flow id is empty
   */
  FlowPage getFlowRecords(String flowId);

  /**
   * Read the page of the records of the flow with the given {@code flowId} that starts at {@code
   * fromPosition}. See {@link #getFlowRecords(String)} for what a page holds.
   *
   * <p>Blocks the calling thread until the records have been fetched. Safe to call on a virtual
   * thread. Use {@link #getFlowRecordsAsync(String, long)} for the non-blocking variant.
   *
   * @param flowId the id of the flow
   * @param fromPosition the {@link FlowPage#nextPosition()} of an earlier page of the flow, or 0 to
   *     read from the start
   * @return the page of the records of the flow that starts at that position
   * @throws IllegalArgumentException if the flow id is empty or the position is negative
   */
  FlowPage getFlowRecords(String flowId, long fromPosition);

  /**
   * Async variant of {@link #getFlowRecords(String)}.
   *
   * @param flowId the id of the flow
   * @return a stage that completes with the first page of the records of the flow, or fails with
   *     {@link IllegalArgumentException} if the flow id is empty
   */
  CompletionStage<FlowPage> getFlowRecordsAsync(String flowId);

  /**
   * Async variant of {@link #getFlowRecords(String, long)}.
   *
   * @param flowId the id of the flow
   * @param fromPosition the {@link FlowPage#nextPosition()} of an earlier page of the flow, or 0 to
   *     read from the start
   * @return a stage that completes with the page of the records of the flow that starts at that
   *     position, or fails with {@link IllegalArgumentException} if the flow id is empty or the
   *     position is negative
   */
  CompletionStage<FlowPage> getFlowRecordsAsync(String flowId, long fromPosition);
}
