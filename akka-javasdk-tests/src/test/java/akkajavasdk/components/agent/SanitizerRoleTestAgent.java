/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

/** Bound by role to the "role-scoped" sanitizer, see test application.conf. */
@Component(id = "sanitizer-role-agent")
@AgentRole("sanitizer-role")
public class SanitizerRoleTestAgent extends Agent {

  public record SomeResponse(String response) {}

  public Effect<SomeResponse> query(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }
}
