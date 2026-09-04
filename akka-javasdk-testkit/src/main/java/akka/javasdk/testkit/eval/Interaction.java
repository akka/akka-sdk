/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.time.Duration;
import java.util.List;

/**
 * What one graded turn produced: the agent's reply as text, and what the runtime traced while
 * producing it. This is what an {@link Evaluator} reads and what a {@link
 * ExperimentRunner.CaseResult} carries.
 *
 * @param text the agent's reply, after any mapping the handler applied
 * @param toolCalls in call order
 * @param modelCalls in call order
 * @param guardrails every guardrail evaluation, in order
 * @param latency from the agent's command start to its end
 * @param finalModelText what the model wrote last, before the agent mapped it; the same as {@code
 *     text} for a plain text handler, and empty when the trace did not carry it
 */
public record Interaction(
    String text,
    List<ToolCall> toolCalls,
    List<ModelCall> modelCalls,
    List<GuardrailResult> guardrails,
    Duration latency,
    String finalModelText) {

  public Interaction {
    text = text == null ? "" : text;
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
    guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
    latency = latency == null ? Duration.ZERO : latency;
    finalModelText = finalModelText == null ? "" : finalModelText;
  }

  /** A reply with tool calls and nothing else traced. */
  public Interaction(String text, List<ToolCall> toolCalls) {
    this(text, toolCalls, List.of(), List.of(), Duration.ZERO, "");
  }

  /** A reply with what the trace held. */
  public Interaction(String text, TracedTurn traced) {
    this(
        text,
        traced.toolCalls(),
        traced.modelCalls(),
        traced.guardrails(),
        traced.duration(),
        traced.finalModelText());
  }

  public static Interaction of(String text) {
    return new Interaction(text, List.of());
  }

  public long inputTokens() {
    return modelCalls.stream().mapToLong(ModelCall::inputTokens).sum();
  }

  public long outputTokens() {
    return modelCalls.stream().mapToLong(ModelCall::outputTokens).sum();
  }

  public long totalTokens() {
    return inputTokens() + outputTokens();
  }

  /** Whether any guardrail blocked the turn. */
  public boolean blocked() {
    return guardrails.stream().anyMatch(g -> !g.passed());
  }
}
