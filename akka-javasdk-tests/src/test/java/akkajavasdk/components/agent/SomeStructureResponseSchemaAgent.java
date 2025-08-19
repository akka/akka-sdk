/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("structured-response-schema-agent")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
public class SomeStructureResponseSchemaAgent extends Agent {

  public record StructuredResponse(String response, int count) {}

  public Effect<StructuredResponse> structuredResponse(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .responseConformsTo(StructuredResponse.class)
        .thenReply();
  }
}
