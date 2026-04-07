/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.UserMessage;
import akka.javasdk.annotations.Component;
import java.net.URI;

@Component(id = "some-multi-modal-user-message-agent")
public class SomeMultiModalUserMessageAgent extends Agent {
  public record SomeResponse(String response) {}

  public Effect<String> ask() {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(
            UserMessage.from(
                MessageContent.TextMessageContent.from("testing"),
                MessageContent.ImageMessageContent.fromUri(URI.create("https://example.com"))))
        .thenReply();
  }
}
