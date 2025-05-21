/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.annotations.TypeName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Record representing the content of a message in the conversation history.
 * For now, only text content is supported.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MessageContent.TextContent.class, name = "text"),
})
public sealed interface MessageContent {

  //@TypeName("akka-text-content")
  record TextContent(String text) implements MessageContent  {}
}
