/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import java.time.Duration;
import java.util.List;

/**
 * Everything the runtime traced under the agent commands of one session, read through {@link
 * TelemetryReader#getAgentTrace(String)}.
 *
 * @param toolCalls in call order
 * @param modelCalls in call order
 * @param guardrails every guardrail evaluation, in order
 * @param duration from the start of the first command to the end of the last
 * @param finalModelText the text of the last model response, before the agent mapped it into the
 *     reply; empty when the trace did not carry the messages
 */
public record AgentTrace(
    List<ToolCall> toolCalls,
    List<ModelCall> modelCalls,
    List<GuardrailResult> guardrails,
    Duration duration,
    String finalModelText) {

  public AgentTrace {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
    guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
    duration = duration == null ? Duration.ZERO : duration;
    finalModelText = finalModelText == null ? "" : finalModelText;
  }

  public static final AgentTrace NONE =
      new AgentTrace(List.of(), List.of(), List.of(), Duration.ZERO, "");
}
