/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.ToolCall;
import java.util.List;

/**
 * The runner's interface to the thing under test: one turn in, one {@link Outcome} out.
 *
 * <p>{@link AgentTarget} is the implementation behind {@link ExperimentRunner#agent}. The runner's
 * own tests supply a lambda to run without a runtime.
 *
 * <p>A turn either answers with the reply and the tool calls, or fails with a reason and the tool
 * calls made before the failure. A target that throws is treated as failed with no tool calls.
 *
 * @param <C> the command the target is called with
 */
@FunctionalInterface
interface EvalTarget<C> {

  Outcome call(Turn<C> turn);

  /**
   * @param sessionId fresh per case, so one case cannot read another's session memory
   * @param caseId the case being run, for a target that logs or routes by it
   * @param command the case's command, as the agent's command handler takes it
   */
  record Turn<C>(String sessionId, String caseId, C command) {

    /** The command as the text the {@link Interaction} carries. */
    String commandText() {
      return Interaction.asText(command);
    }
  }

  /** The result of one turn. */
  sealed interface Outcome {

    /** The tool calls made, also for a failed turn. */
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
        if (reason == null) throw new IllegalArgumentException("reason required");
        if (toolCalls == null) throw new IllegalArgumentException("toolCalls required");
        toolCalls = List.copyOf(toolCalls);
      }
    }
  }
}
