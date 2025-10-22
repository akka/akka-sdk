package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

@ComponentId("empty-agent-name")
@AgentDescription(name = "", description = "Description")
public class AgentWithEmptyAgentDescriptionName extends Agent {

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
