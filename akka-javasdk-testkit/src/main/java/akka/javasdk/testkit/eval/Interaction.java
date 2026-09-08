/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.AgentTrace;
import akka.javasdk.testkit.GuardrailResult;
import akka.javasdk.testkit.ModelCall;
import akka.javasdk.testkit.ToolCall;
import java.time.Duration;
import java.util.List;

/**
 * What one turn produced: the agent's reply as text and what the runtime traced while producing it.
 * This is what an {@link Evaluator} reads.
 *
 * @param reply the agent's reply
 * @param toolCalls in call order
 * @param modelCalls in call order
 * @param guardrails every guardrail evaluation, in order
 * @param latency from the start of the agent command to its end
 * @param finalModelText the text of the last model response, before the agent mapped it into the
 *     reply; empty when the trace did not carry it
 */
public record Interaction(
    String reply,
    List<ToolCall> toolCalls,
    List<ModelCall> modelCalls,
    List<GuardrailResult> guardrails,
    Duration latency,
    String finalModelText) {

  public Interaction {
    if (reply == null) throw new IllegalArgumentException("reply required");
    if (toolCalls == null) throw new IllegalArgumentException("toolCalls required");
    if (modelCalls == null) throw new IllegalArgumentException("modelCalls required");
    if (guardrails == null) throw new IllegalArgumentException("guardrails required");
    if (latency == null) throw new IllegalArgumentException("latency required");
    if (finalModelText == null) throw new IllegalArgumentException("finalModelText required");
    toolCalls = List.copyOf(toolCalls);
    modelCalls = List.copyOf(modelCalls);
    guardrails = List.copyOf(guardrails);
  }

  /** A reply with tool calls only. */
  public Interaction(String reply, List<ToolCall> toolCalls) {
    this(reply, toolCalls, List.of(), List.of(), Duration.ZERO, "");
  }

  /** A reply with the traced evidence. */
  public Interaction(String reply, AgentTrace trace) {
    this(
        reply,
        required(trace).toolCalls(),
        trace.modelCalls(),
        trace.guardrails(),
        trace.duration(),
        trace.finalModelText());
  }

  private static AgentTrace required(AgentTrace trace) {
    if (trace == null) throw new IllegalArgumentException("trace required");
    return trace;
  }

  /** A reply with no evidence. */
  public static Interaction of(String reply) {
    return new Interaction(reply, List.of());
  }

  /** Input tokens summed over the model calls. */
  public long inputTokens() {
    return modelCalls.stream().mapToLong(ModelCall::inputTokens).sum();
  }

  /** Output tokens summed over the model calls. */
  public long outputTokens() {
    return modelCalls.stream().mapToLong(ModelCall::outputTokens).sum();
  }

  /** Input and output tokens together. */
  public long totalTokens() {
    return inputTokens() + outputTokens();
  }

  /** Whether any guardrail blocked the turn. */
  public boolean blocked() {
    return guardrails.stream().anyMatch(g -> !g.passed());
  }
}
