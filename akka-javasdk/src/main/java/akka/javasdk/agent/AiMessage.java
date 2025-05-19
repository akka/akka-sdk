/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public record AiMessage(MessageContent content) implements Message {

  public static AiMessage of(String content) {
    return new AiMessage(new MessageContent.TextContent(content));
  }

  public String getText() {
    return switch (content) {
      case MessageContent.TextContent textContent -> textContent.text();
    };
  }
}
