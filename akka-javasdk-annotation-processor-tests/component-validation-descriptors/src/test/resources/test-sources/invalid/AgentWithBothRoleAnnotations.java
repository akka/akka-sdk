package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

@Component(id = "agent-dual-role")
@AgentDescription(name = "Agent Name", description = "Description", role = "worker")
@AgentRole("supervisor")
public class AgentWithBothRoleAnnotations extends Agent {
  // Has both @AgentDescription.role and @AgentRole - should fail

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
