package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

// tag::class[]
@Component(id = "street-view-agent")
public class StreetViewAgent extends Agent {

  public static class StreetViewService {

    @FunctionTool(description = "Fetches a Street View image for the given location")
    public MessageContent getStreetView(String location) { // <1>
      byte[] image = fetchStreetViewImage(location); // <2>
      return MessageContent.ImageMessageContent.fromBytes(image, "image/jpeg"); // <3>
    }

    private byte[] fetchStreetViewImage(String location) {
      // call the Street View API and return the raw image bytes
      return new byte[0];
    }
  }

  public Effect<String> ask(String question) {
    return effects()
      .systemMessage("You can look up Street View images to answer questions about places.")
      .tools(new StreetViewService())
      .userMessage(question)
      .thenReply();
  }
}
// end::class[]
