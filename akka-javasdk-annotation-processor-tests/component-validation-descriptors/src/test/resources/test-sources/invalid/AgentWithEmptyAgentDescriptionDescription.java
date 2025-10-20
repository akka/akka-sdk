package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("empty-agent-description")
@AgentDescription(name = "Name", description = "")
public class AgentWithEmptyAgentDescriptionDescription extends Agent {

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
