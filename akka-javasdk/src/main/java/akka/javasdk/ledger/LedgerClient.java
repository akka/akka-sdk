/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

/**
 * Client for fetching records from the ledger, the log of recorded agent interactions, their
 * evaluations and the guardrail decisions made in them.
 *
 * <p>A {@code LedgerClient} can be injected into any component. An {@link
 * akka.javasdk.evaluation.Evaluator} uses it to fetch the interaction referenced by its {@link
 * akka.javasdk.evaluation.Subject#interactionId()}.
 *
 * <p>Not for user extension.
 */
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
   * Fetch the interactions of the agent with the given {@code agentComponentId} in the session with
   * the given {@code sessionId}, newest first. The read covers the most recent interactions of the
   * agent, so interactions older than that window are not returned.
   *
   * <p>Blocks the calling thread until the records have been fetched. Safe to call on a virtual
   * thread. Use {@link #getInteractionsAsync(String, String)} for the non-blocking variant.
   *
   * @param agentComponentId the component id of the agent
   * @param sessionId the session the interactions belong to
   * @return the interactions of the agent in that session, or an empty list when none are recorded
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
   * Fetch the guardrail decisions recorded for the agent with the given {@code agentComponentId} in
   * the session with the given {@code sessionId}, newest first. Each record names the interaction
   * the guardrail ran in. The read covers the most recent decisions of the agent, so decisions
   * older than that window are not returned.
   *
   * <p>Blocks the calling thread until the records have been fetched. Safe to call on a virtual
   * thread. Use {@link #getGuardrailDecisionsAsync(String, String)} for the non-blocking variant.
   *
   * @param agentComponentId the component id of the agent
   * @param sessionId the session the decisions belong to
   * @return the guardrail decisions of the agent in that session, or an empty list when none are
   *     recorded
   */
  List<GuardrailDecisionRecord> getGuardrailDecisions(String agentComponentId, String sessionId);

  /**
   * Async variant of {@link #getGuardrailDecisions(String, String)}.
   *
   * @param agentComponentId the component id of the agent
   * @param sessionId the session the decisions belong to
   * @return a stage that completes with the guardrail decisions of the agent in that session
   */
  CompletionStage<List<GuardrailDecisionRecord>> getGuardrailDecisionsAsync(
      String agentComponentId, String sessionId);
}
