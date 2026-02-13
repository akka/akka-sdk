/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import java.util.List;
import java.util.Optional;

/**
 * Internal workflow state for a swarm execution.
 */
public record SwarmState(
    Status status,
    String userMessage,
    int currentTurn,
    int maxTurns,
    Optional<String> currentAgent,
    Optional<String> currentChildSwarm,
    Optional<PauseReason> pauseReason,
    Optional<Object> result,
    Optional<String> failureReason,
    List<String> conversationHistory
) {

  public enum Status {
    RUNNING, PAUSED, COMPLETED, FAILED, STOPPED
  }

  public static SwarmState initial(String userMessage, int maxTurns) {
    return new SwarmState(
        Status.RUNNING, userMessage, 0, maxTurns,
        Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), List.of());
  }

  public SwarmState incrementTurn() {
    return new SwarmState(
        status, userMessage, currentTurn + 1, maxTurns,
        currentAgent, currentChildSwarm, pauseReason,
        result, failureReason, conversationHistory);
  }

  public SwarmState withCurrentAgent(String agent) {
    return new SwarmState(
        status, userMessage, currentTurn, maxTurns,
        Optional.of(agent), Optional.empty(), pauseReason,
        result, failureReason, conversationHistory);
  }

  public SwarmState withCurrentChildSwarm(String childSwarm) {
    return new SwarmState(
        status, userMessage, currentTurn, maxTurns,
        Optional.empty(), Optional.of(childSwarm), pauseReason,
        result, failureReason, conversationHistory);
  }

  public SwarmState paused(PauseReason reason) {
    return new SwarmState(
        Status.PAUSED, userMessage, currentTurn, maxTurns,
        Optional.empty(), Optional.empty(), Optional.of(reason),
        result, failureReason, conversationHistory);
  }

  public SwarmState paused(String contentForReview) {
    return new SwarmState(
        Status.PAUSED, userMessage, currentTurn, maxTurns,
        Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.of(contentForReview), failureReason, conversationHistory);
  }

  public SwarmState resumed(String updatedMessage) {
    return new SwarmState(
        Status.RUNNING, updatedMessage, currentTurn, maxTurns,
        Optional.empty(), Optional.empty(), Optional.empty(),
        result, failureReason, conversationHistory);
  }

  public SwarmState completed(Object resultValue) {
    return new SwarmState(
        Status.COMPLETED, userMessage, currentTurn, maxTurns,
        Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.of(resultValue), Optional.empty(), conversationHistory);
  }

  public SwarmState failed(String reason) {
    return new SwarmState(
        Status.FAILED, userMessage, currentTurn, maxTurns,
        Optional.empty(), Optional.empty(), Optional.empty(),
        result, Optional.of(reason), conversationHistory);
  }

  public SwarmState stopped(String reason) {
    return new SwarmState(
        Status.STOPPED, userMessage, currentTurn, maxTurns,
        Optional.empty(), Optional.empty(), Optional.empty(),
        result, Optional.of(reason), conversationHistory);
  }

  /** Convert to the typed SwarmResult ADT. The unchecked cast is safe because
   *  the runtime ensures the result matches the swarm's declared resultType(). */
  @SuppressWarnings("unchecked")
  public <R> SwarmResult<R> toSwarmResult() {
    return switch (status) {
      case RUNNING -> new SwarmResult.Running<>(currentTurn, maxTurns, currentAgent, currentChildSwarm);
      case PAUSED -> new SwarmResult.Paused<>(pauseReason, result.map(r -> (R) r), currentTurn, maxTurns);
      case COMPLETED -> new SwarmResult.Completed<>((R) result.orElse(null));
      case FAILED -> new SwarmResult.Failed<>(failureReason.orElse("unknown"));
      case STOPPED -> new SwarmResult.Stopped<>(failureReason.orElse("stopped"));
    };
  }
}
