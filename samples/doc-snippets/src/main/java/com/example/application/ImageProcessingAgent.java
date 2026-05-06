package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MessageContent.ImageMessageContent;
import akka.javasdk.agent.MessageContent.TextMessageContent;
import akka.javasdk.agent.UserMessage;
import akka.javasdk.annotations.Component;

@Component(id = "image-processing-agent")
public class ImageProcessingAgent extends Agent {

  // tag::multimodal-user-message[]
  public Effect<String> ask() {
    return effects()
      .systemMessage("You are image analyses tool")
      .userMessage(
        UserMessage.from( // <1>
          TextMessageContent.from("What do you see?"), // <2>
          ImageMessageContent.fromUrl("https://example/image.png") // <3>
        )
      )
      .thenReply();
  }
  // end::multimodal-user-message[]
}
