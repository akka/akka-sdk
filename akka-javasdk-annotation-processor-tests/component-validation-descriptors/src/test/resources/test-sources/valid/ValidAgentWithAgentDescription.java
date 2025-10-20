package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.Component;

@Component(id = "agent-with-description")
@AgentDescription(name = "Agent Name", description = "Agent Description")
public class ValidAgentWithAgentDescription extends Agent {

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
