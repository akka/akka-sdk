/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.MessageContent.DataMessageContent;
import akka.javasdk.agent.MessageContent.ImageUrlMessageContent;
import akka.javasdk.agent.MessageContent.PdfUrlMessageContent;
import akka.javasdk.agent.MessageContent.TextMessageContent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The full record of a single agent interaction, as fetched from the ledger.
 *
 * <p>Beyond the raw fields, this type offers pure convenience accessors — {@link #inputText()},
 * {@link #finalResponseText()}, {@link #toolCalls()}, token totals, {@link #failureSummary()} — and
 * a flattened {@link #transcript()} rendering, for use when evaluating an interaction.
 *
 * @param interactionId the globally unique id of the interaction
 * @param sessionId the id of the session the interaction belongs to
 * @param agentComponentId the component id of the agent that produced the interaction
 * @param flowId the id of the flow this interaction was part of, or empty for a request-based agent
 *     interaction
 * @param metadata metadata about the model call(s) of the interaction
 * @param systemMessage the system message the model was given
 * @param inputMessage the input message content given to the model, in order
 * @param modelResponses the model call(s) of the interaction, in order
 * @param toolCallResponses tool call responses that arrived as input to a model call
 * @param taskContext autonomous-agent task metadata, present only for flow interactions
 * @param failure the failure that terminated the interaction, if it failed
 * @param timestamp when the interaction was recorded
 */
public record InteractionRecord(
    String interactionId,
    String sessionId,
    String agentComponentId,
    Optional<String> flowId,
    InteractionMetadata metadata,
    String systemMessage,
    List<MessageContent> inputMessage,
    List<ModelResponse> modelResponses,
    List<ToolCallResponse> toolCallResponses,
    Optional<TaskContext> taskContext,
    Optional<Failure> failure,
    Instant timestamp) {

  /** Whether this interaction was produced by an agent running as part of a flow. */
  public boolean isFlowInteraction() {
    return flowId.isPresent();
  }

  /** Whether this interaction terminated in a failure. */
  public boolean failed() {
    return failure.isPresent();
  }

  /**
   * The input message rendered as plain text: the text of every {@link TextMessageContent} in
   * {@link #inputMessage()}, joined by newlines. Non-text content (images, PDFs) is omitted.
   */
  public String inputText() {
    return inputMessage.stream()
        .filter(c -> c instanceof TextMessageContent)
        .map(c -> ((TextMessageContent) c).text())
        .collect(Collectors.joining("\n"));
  }

  /**
   * The text content of the last model response that produced any, or an empty string if the model
   * produced no text (for example an interaction that only made tool calls, or that failed).
   */
  public String finalResponseText() {
    for (int i = modelResponses.size() - 1; i >= 0; i--) {
      var content = modelResponses.get(i).content();
      if (content != null && !content.isEmpty()) {
        return content;
      }
    }
    return "";
  }

  /** Every tool call the model requested across all model responses, in order. */
  public List<ToolCall> toolCalls() {
    return modelResponses.stream()
        .flatMap(r -> r.toolCalls().stream())
        .collect(Collectors.toList());
  }

  /** The total number of input tokens across all model calls of the interaction. */
  public int totalInputTokens() {
    return modelResponses.stream().mapToInt(ModelResponse::inputTokenCount).sum();
  }

  /** The total number of output tokens across all model calls of the interaction. */
  public int totalOutputTokens() {
    return modelResponses.stream().mapToInt(ModelResponse::outputTokenCount).sum();
  }

  /**
   * A human-readable summary of the failure that terminated the interaction ({@code reason:
   * description}), or empty if the interaction did not fail.
   */
  public Optional<String> failureSummary() {
    return failure.map(f -> f.reason() + ": " + f.description());
  }

  /**
   * A flattened, ordered, readable transcript of the interaction: the system message, the input
   * message, each model response (including any thinking and tool calls), and the tool call
   * responses the model saw as input.
   *
   * <p>Pure rendering over this record; intended for feeding an interaction to an LLM-as-judge or
   * for logging.
   */
  public String transcript() {
    var sb = new StringBuilder();
    if (systemMessage != null && !systemMessage.isEmpty()) {
      sb.append("System: ").append(systemMessage).append("\n");
    }
    var input =
        inputMessage.stream().map(InteractionRecord::render).collect(Collectors.joining("\n"));
    if (!input.isEmpty()) {
      sb.append("Input: ").append(input).append("\n");
    }
    for (ModelResponse response : modelResponses) {
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
    for (ToolCallResponse toolCallResponse : toolCallResponses) {
      var contents =
          toolCallResponse.contents().stream()
              .map(InteractionRecord::render)
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
