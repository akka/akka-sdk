/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Optional;

/**
 * Typed result ADT for class-based swarms.
 *
 * @param <R> the result type, matching the swarm's {@code resultType()}
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SwarmResult.Running.class, name = "Running"),
    @JsonSubTypes.Type(value = SwarmResult.Paused.class, name = "Paused"),
    @JsonSubTypes.Type(value = SwarmResult.Completed.class, name = "Completed"),
    @JsonSubTypes.Type(value = SwarmResult.Failed.class, name = "Failed"),
    @JsonSubTypes.Type(value = SwarmResult.Stopped.class, name = "Stopped")
})
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
