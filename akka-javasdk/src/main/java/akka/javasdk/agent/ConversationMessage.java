/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.agent.ConversationMessage.AiMessage;
import akka.javasdk.agent.ConversationMessage.UserMessage;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserMessage.class, name = "UM"),
    @JsonSubTypes.Type(value = AiMessage.class, name = "AIM")})
public sealed interface ConversationMessage {

  record UserMessage(String text) implements ConversationMessage {

  }

  record AiMessage(String text) implements ConversationMessage {

  }

}

