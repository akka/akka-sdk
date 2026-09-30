/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.MultimodalToolCallResponse;
import akka.javasdk.agent.SessionMessage.MultimodalUserMessage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.agent.SessionMessage.UserMessage;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.Nulls;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Interface for message representation used inside the SessionMemoryEntity state. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = UserMessage.class, name = "UM"),
  @JsonSubTypes.Type(value = AiMessage.class, name = "AIM"),
  @JsonSubTypes.Type(value = ToolCallResponse.class, name = "TCR"),
  @JsonSubTypes.Type(value = MultimodalUserMessage.class, name = "MUM"),
  @JsonSubTypes.Type(value = MultimodalToolCallResponse.class, name = "MTCR")
})
public sealed interface SessionMessage {
  static int sizeInBytes(String text) {
    return text.length(); // simple implementation, but not correct for all encodings
  }

  static int sizeInBytes(List<MessageContent> contents) {
    return contents.stream()
        .mapToInt(
            content ->
                switch (content) {
                  case MessageContent.TextMessageContent text -> sizeInBytes(text.text());
                  case MessageContent.ImageUriMessageContent image ->
                      sizeInBytes(image.uri())
                          + sizeInBytes(image.detailLevel().toString())
                          + image.mimeType().map(SessionMessage::sizeInBytes).orElse(0);
                  case MessageContent.PdfUriMessageContent pdf -> sizeInBytes(pdf.uri());
                })
        .sum();
  }

  /** The approximate size of this message in bytes, used to enforce memory size limits. */
  int size();

  /** The component id of the agent that produced this message. */
  String componentId();

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = MessageContent.TextMessageContent.class, name = "T"),
    @JsonSubTypes.Type(value = MessageContent.ImageUriMessageContent.class, name = "IU"),
    @JsonSubTypes.Type(value = MessageContent.PdfUriMessageContent.class, name = "PU")
  })
  sealed interface MessageContent {

    public static String IMAGE_PLACEHOLDER = "[image]";
    public static String PDF_PLACEHOLDER = "[pdf]";

    /** Persisted text content. */
    record TextMessageContent(String text) implements MessageContent {}

    /** Persisted image content, referenced by URI rather than inline bytes. */
    record ImageUriMessageContent(
        String uri,
        akka.javasdk.agent.MessageContent.ImageMessageContent.DetailLevel detailLevel,
        Optional<String> mimeType)
        implements MessageContent {}

    /** Persisted PDF content, referenced by URI rather than inline bytes. */
    record PdfUriMessageContent(String uri) implements MessageContent {}
  }

  /**
   * A multimodal user message, e.g. text combined with an image or PDF.
   *
   * @param sanitized true when the message holds the text the model was sent, with the configured
   *     sanitizers applied. The runtime sends such a message to the model as it is. For a message
   *     with false, the runtime masks the text with the current sanitizers before it sends it.
   *     Leave it false in a message that you build.
   */
  record MultimodalUserMessage(
      Instant timestamp, List<MessageContent> contents, String componentId, boolean sanitized)
      implements SessionMessage {

    public MultimodalUserMessage(
        Instant timestamp, List<MessageContent> contents, String componentId) {
      this(timestamp, contents, componentId, false);
    }

    /** returns text from the first MessageContent.TextMessageContent */
    public Optional<String> text() {
      return contents.stream()
          .filter(c -> c instanceof MessageContent.TextMessageContent)
          .map(c -> ((MessageContent.TextMessageContent) c).text())
          .findFirst();
    }

    @Override
    public int size() {
      return contents.stream()
          .map(
              content ->
                  switch (content) {
                    case MessageContent.TextMessageContent text -> sizeInBytes(text.text());
                    case MessageContent.ImageUriMessageContent image ->
                        sizeInBytes(image.uri())
                            + sizeInBytes(image.detailLevel().toString())
                            + image.mimeType.map(SessionMessage::sizeInBytes).orElse(0);
                    case MessageContent.PdfUriMessageContent pdf -> sizeInBytes(pdf.uri());
                  })
          .mapToInt(Integer::intValue)
          .sum();
    }
  }

  /**
   * A plain text user message.
   *
   * @param sanitized true when the message holds the text the model was sent, with the configured
   *     sanitizers applied. The runtime sends such a message to the model as it is. For a message
   *     with false, the runtime masks the text with the current sanitizers before it sends it.
   *     Leave it false in a message that you build.
   */
  record UserMessage(Instant timestamp, String text, String componentId, boolean sanitized)
      implements SessionMessage {

    public UserMessage(Instant timestamp, String text, String componentId) {
      this(timestamp, text, componentId, false);
    }

    public UserMessage(Instant now, String text) {
      this(now, text, "", false);
    }

    @Override
    public int size() {
      return sizeInBytes(text);
    }
  }

  /** A tool call requested by the model as part of an {@link AiMessage}. */
  record ToolCallRequest(String id, String name, String arguments) {}

  /** Token usage for a single {@link AiMessage}. */
  record TokenUsage(int inputTokens, int outputTokens) {
    /** No tokens consumed. */
    public static final TokenUsage EMPTY = new TokenUsage(0, 0);

    /** The sum of this and another usage. */
    public TokenUsage add(TokenUsage tokenUsage) {
      return new TokenUsage(
          inputTokens + tokenUsage.inputTokens, outputTokens + tokenUsage.outputTokens);
    }
  }

  /**
   * The model's reply, with any tool calls it requested and the token usage it incurred.
   *
   * @param sanitized true when the message holds the model output as the model produced it. The
   *     runtime sends such a message to the model as it is. For a message with false, the runtime
   *     masks the text with the current sanitizers before it sends it. Leave it false in a message
   *     that you build.
   */
  record AiMessage(
      Instant timestamp,
      String text,
      String componentId,
      List<ToolCallRequest> toolCallRequests,
      Optional<String> thinking,
      TokenUsage tokenUsage,
      @JsonSetter(nulls = Nulls.AS_EMPTY) Map<String, Object> attributes,
      boolean sanitized)
      implements SessionMessage {

    public AiMessage(
        Instant timestamp,
        String text,
        String componentId,
        List<ToolCallRequest> toolCallRequests,
        Optional<String> thinking,
        TokenUsage tokenUsage,
        Map<String, Object> attributes) {
      this(timestamp, text, componentId, toolCallRequests, thinking, tokenUsage, attributes, false);
    }

    public AiMessage(
        Instant timestamp,
        String text,
        String componentId,
        List<ToolCallRequest> toolCallRequests,
        Optional<String> thinking) {
      this(timestamp, text, componentId, toolCallRequests, thinking, TokenUsage.EMPTY, Map.of());
    }

    public AiMessage(
        Instant timestamp,
        String text,
        String componentId,
        List<ToolCallRequest> toolCallRequests) {
      this(
          timestamp,
          text,
          componentId,
          toolCallRequests,
          Optional.empty(),
          TokenUsage.EMPTY,
          Map.of());
    }

    public AiMessage(Instant timestamp, String text, String componentId) {
      this(timestamp, text, componentId, List.of(), Optional.empty(), TokenUsage.EMPTY, Map.of());
    }

    public AiMessage(Instant timestamp, String text, String componentId, TokenUsage tokenUsage) {
      this(timestamp, text, componentId, List.of(), Optional.empty(), tokenUsage, Map.of());
    }

    @Override
    public int size() {
      int textLength = text == null ? 0 : SessionMessage.sizeInBytes(text);
      int thinkingLength = thinking.map(SessionMessage::sizeInBytes).orElse(0);
      // calculating the length of tool call requests arguments
      // NOTE: not accounting for the real payload, only the arguments
      int argsLength =
          toolCallRequests == null
              ? 0
              : toolCallRequests.stream()
                  .mapToInt(
                      req ->
                          req.arguments() == null ? 0 : SessionMessage.sizeInBytes(req.arguments()))
                  .sum();

      return textLength + thinkingLength + argsLength;
    }
  }

  /**
   * The text result of a tool call, fed back to the model as input.
   *
   * @param sanitized true when the message holds the tool result the model was sent, with the
   *     configured sanitizers applied. The runtime sends such a message to the model as it is. For
   *     a message with false, the runtime masks the text with the current sanitizers before it
   *     sends it. Leave it false in a message that you build.
   */
  record ToolCallResponse(
      Instant timestamp, String componentId, String id, String name, String text, boolean sanitized)
      implements SessionMessage {

    public ToolCallResponse(
        Instant timestamp, String componentId, String id, String name, String text) {
      this(timestamp, componentId, id, name, text, false);
    }

    @Override
    public int size() {
      return SessionMessage.sizeInBytes(text);
    }
  }

  /**
   * The multimodal result of a tool call, fed back to the model as input.
   *
   * @param sanitized true when the message holds the tool result the model was sent, with the
   *     configured sanitizers applied. The runtime sends such a message to the model as it is. For
   *     a message with false, the runtime masks the text with the current sanitizers before it
   *     sends it. Leave it false in a message that you build.
   */
  record MultimodalToolCallResponse(
      Instant timestamp,
      String componentId,
      String id,
      String name,
      List<MessageContent> contents,
      boolean sanitized)
      implements SessionMessage {

    public MultimodalToolCallResponse(
        Instant timestamp,
        String componentId,
        String id,
        String name,
        List<MessageContent> contents) {
      this(timestamp, componentId, id, name, contents, false);
    }

    @Override
    public int size() {
      return SessionMessage.sizeInBytes(contents);
    }
  }
}
