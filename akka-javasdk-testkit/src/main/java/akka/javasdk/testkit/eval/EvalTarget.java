/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.List;

/**
 * The runner's seam to the thing under test: one graded turn in, one {@link Outcome} out.
 *
 * <p>Not part of the API. A consumer names an agent through {@link ExperimentRunner#forAgent}, and
 * {@link AgentTarget} is the implementation behind it. The runner's own tests supply a lambda here
 * to run without a runtime.
 *
 * <p>A turn either {@link Outcome.Answered answers} with the reply and the tool calls, or {@link
 * Outcome.Failed fails} with the reason and whatever tool calls happened before it. The runner
 * records a failed turn as a failed case, not as a wrong answer, and the report separates the two.
 * A target that throws instead is treated as failed with no evidence.
 */
@FunctionalInterface
interface EvalTarget {

  Outcome call(Turn turn);

  /**
   * @param sessionId fresh per case, so one case cannot read another's session memory
   * @param caseId which case is being asked, for a target that logs or routes by it
   */
  record Turn(String sessionId, String caseId, String userMessage) {}

  /** What one turn came to. */
  sealed interface Outcome {

    /** The tool calls seen either way, so a failed turn still shows what the agent did. */
    List<ToolCall> toolCalls();

    static Outcome answered(Interaction interaction) {
      return new Answered(interaction);
    }

    static Outcome failed(String reason, List<ToolCall> toolCalls) {
      return new Failed(reason, toolCalls);
    }

    static Outcome failed(RuntimeException cause, List<ToolCall> toolCalls) {
      var message = cause.getMessage() == null ? "" : ": " + cause.getMessage();
      return new Failed(cause.getClass().getSimpleName() + message, toolCalls);
    }

    record Answered(Interaction interaction) implements Outcome {
      public Answered {
        if (interaction == null) throw new IllegalArgumentException("interaction required");
      }

      @Override
      public List<ToolCall> toolCalls() {
        return interaction.toolCalls();
      }
    }

    record Failed(String reason, List<ToolCall> toolCalls) implements Outcome {
      public Failed {
        reason = reason == null ? "" : reason;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
      }
    }
  }
}
