/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.CompoundUserMessage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.agent.SessionMessage.UserMessage;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Interface for message representation used inside the SessionMemoryEntity state. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = UserMessage.class, name = "UM"),
  @JsonSubTypes.Type(value = AiMessage.class, name = "AIM"),
  @JsonSubTypes.Type(value = ToolCallResponse.class, name = "TCR"),
  @JsonSubTypes.Type(value = CompoundUserMessage.class, name = "CUM")
})
public sealed interface SessionMessage {
  static int sizeInBytes(String text) {
    return text.length(); // simple implementation, but not correct for all encodings
  }

  int size();

  String componentId();

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = MessageContent.TextMessageContent.class, name = "T"),
    @JsonSubTypes.Type(value = MessageContent.ImageUriMessageContent.class, name = "IU")
  })
  sealed interface MessageContent {

    record TextMessageContent(String text) implements MessageContent {}

    record ImageUriMessageContent(
        String uri, akka.javasdk.agent.MessageContent.ImageMessageContent.DetailLevel detailLevel)
        implements MessageContent {}
  }

  // need to introduce new message to keep backward compatibility
  record CompoundUserMessage(Instant timestamp, List<MessageContent> contents, String componentId)
      implements SessionMessage {

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
                        sizeInBytes(image.uri()) + sizeInBytes(image.detailLevel().toString());
                  })
          .mapToInt(Integer::intValue)
          .sum();
    }
  }

  record UserMessage(Instant timestamp, String text, String componentId) implements SessionMessage {

    public UserMessage(Instant now, String text) {
      this(now, text, "");
    }

    @Override
    public int size() {
      return sizeInBytes(text);
    }
  }

  record ToolCallRequest(String id, String name, String arguments) {}

  record AiMessage(
      Instant timestamp, String text, String componentId, List<ToolCallRequest> toolCallRequests)
      implements SessionMessage {

    public AiMessage(Instant timestamp, String text, String componentId) {
      this(timestamp, text, componentId, List.of());
    }

    @Override
    public int size() {
      var textLength = text == null ? 0 : SessionMessage.sizeInBytes(text);
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

      return textLength + argsLength;
    }
  }

  record ToolCallResponse(
      Instant timestamp, String componentId, String id, String name, String text)
      implements SessionMessage {
    @Override
    public int size() {
      return SessionMessage.sizeInBytes(text);
    }
  }
}
