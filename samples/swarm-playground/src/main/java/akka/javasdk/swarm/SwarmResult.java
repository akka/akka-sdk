/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import java.util.Optional;

/**
 * Typed result ADT for class-based swarms.
 *
 * @param <R> the result type, matching the swarm's {@code resultType()}
 */
public sealed interface SwarmResult<R> {

  record Running<R>(int currentTurn, int maxTurns,
                    Optional<String> currentAgent,
                    Optional<String> currentChildSwarm) implements SwarmResult<R> {}

  record Paused<R>(Optional<PauseReason> reason, Optional<R> contentForReview,
                   int currentTurn, int maxTurns) implements SwarmResult<R> {}

  /** The swarm completed successfully with a fully-typed result. */
  record Completed<R>(R result) implements SwarmResult<R> {}

  record Failed<R>(String reason) implements SwarmResult<R> {}

  record Stopped<R>(String reason) implements SwarmResult<R> {}
}
