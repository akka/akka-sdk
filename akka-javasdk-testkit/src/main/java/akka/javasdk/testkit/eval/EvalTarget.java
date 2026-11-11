/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.Optional;

/**
 * The runner's interface to the thing under test: one turn in, the id of the interaction it
 * recorded out. What the turn left in the ledger is read by the runner.
 *
 * <p>{@link AgentTarget} is the implementation behind {@link ExperimentCases#agent}. The runner's
 * own tests supply a lambda to run without a runtime.
 *
 * @param <C> the command the target is called with
 */
@FunctionalInterface
interface EvalTarget<C> {

  /**
   * Calls the target in the turn's session. Answers with the interaction id, or fails with a
   * reason. A target that throws is treated as failed.
   */
  Outcome call(Turn<C> turn);

  /**
   * @param sessionId fresh per attempt, so one attempt cannot read another's session memory
   * @param caseId the case being run, for a target that logs or routes by it
   * @param command the case's command, as the agent's command handler takes it
   */
  record Turn<C>(String sessionId, String caseId, C command) {}

  /** The result of one turn. */
  sealed interface Outcome {

    static Outcome answered(Optional<String> interactionId) {
      return new Answered(interactionId, Optional.empty());
    }

    static Outcome answered(String interactionId) {
      return new Answered(Optional.of(interactionId), Optional.empty());
    }

    static Outcome answered(Optional<String> interactionId, Optional<?> value) {
      return new Answered(interactionId, value.map(v -> v));
    }

    static Outcome failed(String reason) {
      return new Failed(reason);
    }

    static Outcome failed(RuntimeException cause) {
      var message = cause.getMessage() == null ? "" : ": " + cause.getMessage();
      return new Failed(cause.getClass().getSimpleName() + message);
    }

    /**
     * @param interactionId the id of the interaction the turn recorded; empty when the reply
     *     carried none
     * @param value the value the target returned; empty when the target gave none
     */
    record Answered(Optional<String> interactionId, Optional<Object> value) implements Outcome {
      public Answered {
        if (interactionId == null) throw new IllegalArgumentException("interactionId required");
        if (value == null) throw new IllegalArgumentException("value required");
      }

      /** The returned value as text: a String as is, any other value as JSON. */
      Optional<String> returned() {
        return value.map(EvalCase::asText);
      }
    }

    /**
     * @param reason why the turn failed
     */
    record Failed(String reason) implements Outcome {
      public Failed {
        if (reason == null) throw new IllegalArgumentException("reason required");
      }
    }
  }
}
