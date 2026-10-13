/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

/**
 * Bound by component id to the "agent-scoped" and "recording" sanitizers, see test
 * application.conf.
 */
@Component(id = "sanitizer-test-agent")
public class SanitizerTestAgent extends Agent {

  public record SomeResponse(String response) {}

  public Effect<SomeResponse> query(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }

  @FunctionTool(description = "Returns the notes for a customer")
  private String getNotes(String customer) {
    return "Notes for " + customer + ": toolsecret and modelsecret and disabledsecret";
  }
}
