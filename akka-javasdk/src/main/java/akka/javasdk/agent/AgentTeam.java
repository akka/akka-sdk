/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.Done;
import akka.javasdk.impl.agent.DelegationImpl;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Optional;

public interface AgentTeam<A, B> {

  interface Delegation {
    String agentComponentId();

    Optional<String> description();

    Delegation withDescription(String description);

    public static Delegation toAgent(Class<? extends Agent> agentClass) {
      return DelegationImpl.toAgent(agentClass);
    }
  }

  // FIXME we will have more types, like Paused, Stopped, and probably more information
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Result.Running.class, name = "Running"),
    @JsonSubTypes.Type(value = Result.Completed.class, name = "Completed"),
    @JsonSubTypes.Type(value = Result.Failed.class, name = "Failed")
  })
  sealed interface Result<R> {
    record Running<R>(int currentTurn) implements Result<R> {}

    record Completed<R>(R result) implements Result<R> {}

    record Failed<R>(String reason) implements Result<R> {}
  }

  /** System instructions for the orchestrator LLM. */
  String instructions();

  /** Delegation targets available to the orchestrator. */
  List<Delegation> delegations();

  /** Expected result type; conforming LLM output terminates the agent. */
  Class<B> resultType(); // FIXME is this needed, or can we get it from the type parameter?

  /** Max LLM round-trips (default: 10). */
  default int maxTurns() {
    return 10;
  }

  /** Memory provider for session history (default: from config). */
  default MemoryProvider memoryProvider() {
    return MemoryProvider.fromConfig();
  }

  /** Tools available to the orchestrator LLM (default: none). */
  default List<Object> tools() {
    return List.of();
  }

  /** Accessor for the input to the {@code run} method, available in all methods. */
  @SuppressWarnings("unchecked")
  default A getInput() {
    return (A) ((Agent) this)._getAgentTeamInput();
  }

  default Agent.Effect<Done> run(A input) {
    return null;
  }

  default Agent.Effect<Result<B>> getResult() {
    // FIXME we might need Agent.ReadOnlyEffect
    return null;
  }
}
