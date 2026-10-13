/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

/**
 * An agent whose interactions are evaluated by {@link ResponseQualityEvaluator}, bound through its
 * role rather than its component id.
 */
@Component(
    id = "role-evaluated-agent",
    name = "Role Evaluated Agent",
    description = "A support agent evaluated through a binding by its role.")
@AgentRole("evaluated-by-role")
public class RoleEvaluatedAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
        .systemMessage("You are a helpful support agent.")
        .userMessage(question)
        .thenReply();
  }
}
