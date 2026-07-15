/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.util.List;

/**
 * A single model call within an interaction.
 *
 * @param id the id of this model response, a seam for correlating finer-grained records
 * @param content the text content the model produced
 * @param inputTokenCount the number of tokens in the input to this model call
 * @param outputTokenCount the number of tokens the model produced
 * @param thinking the model's reasoning/thinking output, or empty if none was recorded
 * @param toolCalls the tool calls the model requested within this response, in order
 */
public record ModelResponse(
    String id,
    String content,
    int inputTokenCount,
    int outputTokenCount,
    String thinking,
    List<ToolCall> toolCalls) {}
