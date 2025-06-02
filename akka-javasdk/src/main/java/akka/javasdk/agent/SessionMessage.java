/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.UserMessage;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserMessage.class, name = "UM"),
  @JsonSubTypes.Type(value = AiMessage.class, name = "AIM")})
public sealed interface SessionMessage {

  int size();

  record UserMessage(String text, int tokens) implements SessionMessage {

    @Override
    public int size() {
      return text.length();
    }
  }

  record ToolCallRequest(String id, String name, String arguments)  {
  }
  record ToolCallResponse(String id, String name, String content) {
  }
  record ToolCallInteraction(ToolCallRequest request, ToolCallResponse response) {
  }
  
  record AiMessage(String text, int tokens, List<ToolCallInteraction> toolCallInteractions) implements SessionMessage {
    public AiMessage(String text, int tokens) {
      this(text, tokens, List.of());
    }

    @Override
    public int size() {
      return text.length();
    }
  }

  
}

