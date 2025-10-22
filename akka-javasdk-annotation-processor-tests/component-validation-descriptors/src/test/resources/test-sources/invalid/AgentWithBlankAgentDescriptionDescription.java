package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("blank-agent-description")
@AgentDescription(name = "Name", description = "   ")
public class AgentWithBlankAgentDescriptionDescription extends Agent {

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
