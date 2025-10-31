/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.agent.MessageContent.TextMessageContent;
import java.util.List;

public record UserMessage(List<MessageContent> contents) {

  public static UserMessage from(String text) {
    return new UserMessage(List.of(TextMessageContent.from(text)));
  }
}
