/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.JsonParsingException;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("structured-response-agent")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
public class SomeStructureResponseAgent extends Agent {
  private static final Logger log = LoggerFactory.getLogger(SomeStructureResponseAgent.class);

  public record SomeResponse(String response) {}
  public record StructuredResponse(String response) {}

  public Effect<SomeResponse> mapStructureResponse(String question) {
    return effects()
      .systemMessage("You are a helpful...")
      .userMessage(question)
      .responseAs(StructuredResponse.class)
      .map(r -> new SomeResponse(r.response()))
      .onFailure(throwable -> {
        if (throwable instanceof JsonParsingException jsonParsingException){
          log.info("Raw json: {}", jsonParsingException.getRawJson());
          return new SomeResponse("default response");
        }else {
          throw new RuntimeException(throwable);
        }
      })
      .thenReply();
  }
}
