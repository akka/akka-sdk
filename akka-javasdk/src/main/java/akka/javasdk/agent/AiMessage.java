/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;


public record AiMessage(MessageContent content) implements Message {

  public static AiMessage of(String text) {
    return new AiMessage(new MessageContent.TextContent(text));
  }

  public String getText() {
    return switch (content) {
      case MessageContent.TextContent textContent -> textContent.text();
    };
  }
}
