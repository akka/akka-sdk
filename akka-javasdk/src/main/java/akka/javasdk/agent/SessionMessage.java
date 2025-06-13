/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.agent.SessionMessage.UserMessage;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;


/**
 * Interface for message representation used inside SessionMemoryEntity state.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = UserMessage.class, name = "UM"),
  @JsonSubTypes.Type(value = AiMessage.class, name = "AIM"),
  @JsonSubTypes.Type(value = ToolCallResponse.class, name = "TCR")})
public sealed interface SessionMessage {

  int size();

  String text();

  record UserMessage(Instant timestamp, String text, String componentId) implements SessionMessage {

    @Override
    public int size() {
      return text.length();
    }
  }

  record ToolCallRequest(String id, String name, String arguments)  {
  }

  record AiMessage(Instant timestamp,
                   String text,
                   String componentId,
                   int inputTokens,
                   int outputTokens,
                   List<ToolCallRequest> toolCallRequests) implements SessionMessage {

    public AiMessage(Instant timestamp, String text, String componentId, int inputTokens, int outputTokens) {
      this(timestamp, text, componentId, inputTokens, outputTokens, List.of());
    }

    @Override
    public int size() {
      var textLength = text == null ? 0 : text.length();
      // calculating the length of tool call requests arguments
      // NOTE: not accounting for the real payload, only the arguments
      int argsLength = toolCallRequests == null ? 0 : toolCallRequests.stream()
          .mapToInt(req -> req.arguments() == null ? 0 : req.arguments().length())
          .sum();

      return textLength + argsLength;
    }
  }

  record ToolCallResponse(Instant timestamp, String componentId, String id, String name, String text) implements SessionMessage {
    @Override
    public int size() {
      return text.length();
    }
  }

}

