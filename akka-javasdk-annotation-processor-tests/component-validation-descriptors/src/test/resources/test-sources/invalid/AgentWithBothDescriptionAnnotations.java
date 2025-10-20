package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.Component;

@Component(id = "agent-dual-desc", name = "Agent Name", description = "Component Description")
@AgentDescription(name = "Agent Desc Name", description = "Agent Description")
public class AgentWithBothDescriptionAnnotations extends Agent {
  // Has both @Component name/description and @AgentDescription name/description - should fail

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
