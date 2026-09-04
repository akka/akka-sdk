/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.time.Duration;
import java.util.List;

/**
 * Everything the runtime traced under an agent's command: the evidence of one turn before it is
 * paired with the reply.
 *
 * @param toolCalls in call order
 * @param modelCalls in call order; the last one produced the final text
 * @param guardrails every guardrail evaluation, in order
 * @param duration from the command's start to its end
 * @param finalModelText what the model wrote in its last call, before the agent mapped it into a
 *     reply; empty when the trace did not carry the messages
 */
public record TracedTurn(
    List<ToolCall> toolCalls,
    List<ModelCall> modelCalls,
    List<GuardrailResult> guardrails,
    Duration duration,
    String finalModelText) {

  public TracedTurn {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
    guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
    duration = duration == null ? Duration.ZERO : duration;
    finalModelText = finalModelText == null ? "" : finalModelText;
  }

  public static final TracedTurn NONE =
      new TracedTurn(List.of(), List.of(), List.of(), Duration.ZERO, "");
}
