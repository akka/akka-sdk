/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("structured-response-agent")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
public class SomeStructureResponseAgent extends Agent {
  private final ModelProvider modelProvider;

  public record SomeResponse(String response) {}
  public record StructuredResponse(String response) {}

  public SomeStructureResponseAgent(ModelProvider modelProvider) {
    this.modelProvider = modelProvider;
  }


  public Effect<SomeResponse> mapStructureResponse(String question) {
    return effects()
      .model(modelProvider)
      .systemMessage("You are a helpful...")
      .userMessage(question)
      .responseAs(StructuredResponse.class)
      .map(r -> new SomeResponse(r.response()))
      .reply();
  }
}
