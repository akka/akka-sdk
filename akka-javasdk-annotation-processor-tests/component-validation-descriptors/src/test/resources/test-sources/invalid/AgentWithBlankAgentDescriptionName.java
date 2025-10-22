package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("blank-agent-name")
@AgentDescription(name = "   ", description = "Description")
public class AgentWithBlankAgentDescriptionName extends Agent {

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
