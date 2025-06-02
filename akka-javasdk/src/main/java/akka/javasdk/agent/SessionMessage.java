/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.UserMessage;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


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

  record AiMessage(String text, int tokens) implements SessionMessage {

    @Override
    public int size() {
      return text.length();
    }
  }

}

