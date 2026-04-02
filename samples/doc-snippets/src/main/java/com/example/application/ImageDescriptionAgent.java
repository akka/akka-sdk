package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.UserMessage;
import akka.javasdk.annotations.Component;

@Component(id = "image-description-agent")
public class ImageDescriptionAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You are an expert image analyst. When given an image, provide a clear and detailed
    description of what you see, including subjects, colors, composition, mood, and any
    notable details.
    """;

  // tag::describe[]
  public Effect<String> describe(MessageContent.ImageUrlMessageContent imageContent) {
    var userMessage = UserMessage.from(
      MessageContent.TextMessageContent.from("Please describe this image in detail."),
      imageContent // <1>
    );
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(userMessage).thenReply();
  }
  // end::describe[]
}
