/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.MessageContent.DataMessageContent;
import akka.javasdk.agent.MessageContent.ImageUrlMessageContent;
import akka.javasdk.agent.MessageContent.PdfUrlMessageContent;
import akka.javasdk.agent.MessageContent.TextMessageContent;
import akka.javasdk.ledger.Failure;
import akka.javasdk.ledger.InteractionMetadata.FinishReason;
import akka.javasdk.ledger.ModelConfig;
import akka.javasdk.ledger.ModelResponse;
import akka.javasdk.ledger.TaskContext;
import akka.javasdk.ledger.ToolCall;
import akka.javasdk.ledger.ToolCallResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * The content of an interaction under evaluation, whatever source it came from.
 *
 * <p>Carries the fields of a ledger {@link akka.javasdk.ledger.InteractionRecord}, with a field
 * optional where not every source supplies it: an authored dataset item has no token counts or
 * timings, because no model ran to produce them; a trace carries whatever its instrumentation
 * emitted. An evaluator that finds a field empty reports inconclusive, the same answer an assertion
 * gives over an external target it cannot fully observe.
 *
 * <p>Not for user extension.
 */
public interface Interaction {

  /** The stable id of the interaction. */
  String interactionId();

  /** The system message the model was given, or empty if none. */
  String systemMessage();

  /** The input message content given to the model, in order. */
  List<MessageContent> inputMessage();

  /** The model call(s) of the interaction, in order. */
  List<ModelResponse> modelResponses();

  /** Tool call responses that arrived as input to a model call. */
  List<ToolCallResponse> toolCallResponses();

  /** The failure that terminated the interaction, if it failed. */
  Optional<Failure> failure();

  /** The component id of the agent that produced the interaction, where known. */
  Optional<String> agentComponentId();

  /** The id of the session the interaction belongs to, where known. */
  Optional<String> sessionId();

  /** The id of the flow this interaction was part of, where the interaction belongs to one. */
  Optional<String> flowId();

  /**
   * Autonomous-agent task metadata, present only for a flow interaction whose source supplies it.
   */
  Optional<TaskContext> taskContext();

  /** When the model call started, where known. */
  Optional<Instant> callStartedAt();

  /** When the model call finished, where known. */
  Optional<Instant> callFinishedAt();

  /** Why the model call finished, where known. */
  Optional<FinishReason> finishReason();

  /** The model configuration used for the interaction, where known. */
  Optional<ModelConfig> modelConfig();

  /** The total number of input tokens across all model calls, where known. */
  OptionalInt totalInputTokens();

  /** The total number of output tokens across all model calls, where known. */
  OptionalInt totalOutputTokens();

  /** Every tool call the model requested across all model responses, in order. */
  default List<ToolCall> toolCalls() {
    return modelResponses().stream()
        .flatMap(r -> r.toolCalls().stream())
        .collect(Collectors.toList());
  }

  /**
   * The input message rendered as plain text: the text of every {@link TextMessageContent} in
   * {@link #inputMessage()}, joined by newlines. Non-text content (images, PDFs) is omitted.
   */
  default String inputText() {
    return inputMessage().stream()
        .filter(c -> c instanceof TextMessageContent)
        .map(c -> ((TextMessageContent) c).text())
        .collect(Collectors.joining("\n"));
  }

  /**
   * The text content of the last model response that produced any, or an empty string if the model
   * produced no text (for example an interaction that only made tool calls, or that failed).
   */
  default String finalResponseText() {
    var responses = modelResponses();
    for (int i = responses.size() - 1; i >= 0; i--) {
      var content = responses.get(i).content();
      if (content != null && !content.isEmpty()) {
        return content;
      }
    }
    return "";
  }

  /**
   * A flattened, ordered, readable transcript of the interaction: the system message, the input
   * message, each model response (including any thinking and tool calls), and the tool call
   * responses the model saw as input.
   *
   * <p>Pure rendering over this content; intended for feeding an interaction to an LLM-as-judge or
   * for logging.
   */
  default String transcript() {
    var sb = new StringBuilder();
    if (systemMessage() != null && !systemMessage().isEmpty()) {
      sb.append("System: ").append(systemMessage()).append("\n");
    }
    var input = inputMessage().stream().map(Interaction::render).collect(Collectors.joining("\n"));
    if (!input.isEmpty()) {
      sb.append("Input: ").append(input).append("\n");
    }
    for (ModelResponse response : modelResponses()) {
      if (response.thinking() != null && !response.thinking().isEmpty()) {
        sb.append("Thinking: ").append(response.thinking()).append("\n");
      }
      if (response.content() != null && !response.content().isEmpty()) {
        sb.append("Response: ").append(response.content()).append("\n");
      }
      for (ToolCall toolCall : response.toolCalls()) {
        sb.append("Tool call ")
            .append(toolCall.name())
            .append("(")
            .append(toolCall.arguments())
            .append(") -> ")
            .append(toolCall.response())
            .append("\n");
      }
    }
    for (ToolCallResponse toolCallResponse : toolCallResponses()) {
      var contents =
          toolCallResponse.contents().stream()
              .map(Interaction::render)
              .collect(Collectors.joining("\n"));
      sb.append("Tool response ")
          .append(toolCallResponse.name())
          .append(": ")
          .append(contents)
          .append("\n");
    }
    return sb.toString();
  }

  private static String render(MessageContent content) {
    if (content instanceof TextMessageContent text) {
      return text.text();
    } else if (content instanceof ImageUrlMessageContent image) {
      return "[image " + image.uri() + "]";
    } else if (content instanceof PdfUrlMessageContent pdf) {
      return "[pdf " + pdf.uri() + "]";
    } else if (content instanceof DataMessageContent) {
      return "[binary content]";
    } else {
      return "[content]";
    }
  }
}
