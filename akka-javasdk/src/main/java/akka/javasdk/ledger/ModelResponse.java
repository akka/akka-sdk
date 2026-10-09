/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import akka.javasdk.agent.Agent;
import java.util.List;

/**
 * A single model call within an interaction.
 *
 * @param id the id of this model response, a seam for correlating finer-grained records
 * @param content the text content the model produced
 * @param tokenUsage the tokens this model call consumed and produced. Interactions recorded before
 *     the prompt cache counts were recorded report no cache activity.
 * @param thinking the model's reasoning/thinking output, or empty if none was recorded
 * @param toolCalls the tool calls the model requested within this response, in order
 */
public record ModelResponse(
    String id,
    String content,
    Agent.TokenUsage tokenUsage,
    String thinking,
    List<ToolCall> toolCalls) {

  /** A model response with no prompt cache activity. */
  public ModelResponse(
      String id,
      String content,
      int inputTokenCount,
      int outputTokenCount,
      String thinking,
      List<ToolCall> toolCalls) {
    this(id, content, new Agent.TokenUsage(inputTokenCount, outputTokenCount), thinking, toolCalls);
  }

  /** The number of tokens in the input to this model call, as reported by the provider. */
  public int inputTokenCount() {
    return tokenUsage.inputTokens();
  }

  /** The number of tokens the model produced. */
  public int outputTokenCount() {
    return tokenUsage.outputTokens();
  }
}
