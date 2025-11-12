/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.UserMessage;
import akka.javasdk.annotations.Component;

@Component(id = "some-multi-modal-user-message-agent")
public class SomeMultiModalUserMessageAgent extends Agent {
  public record SomeResponse(String response) {}

  public Effect<String> ask() {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(
            UserMessage.from(
                MessageContent.TextMessageContent.from("testing"),
                MessageContent.ImageMessageContent.fromUrl("https://example.com")))
        .thenReply();
  }
}
