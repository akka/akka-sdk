/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.AgentTrace;
import akka.javasdk.testkit.ToolCall;
import java.util.List;

/**
 * The runner's interface to the thing under test: one turn in, one {@link Outcome} out.
 *
 * <p>{@link AgentTarget} is the implementation behind {@link ExperimentRunner#agent}. The runner's
 * own tests supply a lambda to run without a runtime.
 *
 * <p>A turn either answers with the reply and the evidence, or fails with a reason and the evidence
 * traced before the failure. A target that throws is treated as failed with no evidence.
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

    /** A failure with the tool calls made before it as the only evidence. */
    static Outcome failed(String reason, List<ToolCall> toolCalls) {
      return new Failed(reason, new AgentTrace(toolCalls, null, null, null, null, null));
    }

    static Outcome failed(String reason, AgentTrace trace) {
      return new Failed(reason, trace);
    }

    static Outcome failed(RuntimeException cause, AgentTrace trace) {
      var message = cause.getMessage() == null ? "" : ": " + cause.getMessage();
      return new Failed(cause.getClass().getSimpleName() + message, trace);
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

    /**
     * @param reason why the turn failed
     * @param trace what the runtime traced before the failure, such as the tool calls, the model
     *     calls and the guardrail that blocked the turn
     */
    record Failed(String reason, AgentTrace trace) implements Outcome {
      public Failed {
        if (reason == null) throw new IllegalArgumentException("reason required");
        if (trace == null) throw new IllegalArgumentException("trace required");
      }

      @Override
      public List<ToolCall> toolCalls() {
        return trace.toolCalls();
      }
    }
  }
}
