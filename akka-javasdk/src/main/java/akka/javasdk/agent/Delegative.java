/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.Done;
import akka.javasdk.impl.agent.DelegationImpl;
import java.util.List;
import java.util.Optional;

public interface Delegative<A, B> {

  interface Delegation {
    String agentComponentId();

    Optional<String> description();

    Delegation withDescription(String description);

    public static Delegation toAgent(Class<? extends Agent> agentClass) {
      return DelegationImpl.toAgent(agentClass);
    }
  }

  // FIXME we will have more types, like Paused, Stopped, and probably more information
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
  Class<B> resultType();

  /** Max LLM round-trips (default: 10). */
  default int maxTurns() {
    return 10;
  }

  /** Tools available to the orchestrator LLM (default: none). */
  default List<Object> tools() {
    return List.of();
  }

  /** Accessor for the input to the {@code run} method, available in all methods. */
  default A getInput() {
    return null; // FIXME override implementation of this in Agent?
  }

  default Agent.Effect<Done> run(A input) {
    return null;
  }

  default Agent.Effect<Result<B>> getResult() {
    // FIXME we might need Agent.ReadOnlyEffect
    return null;
  }
}
