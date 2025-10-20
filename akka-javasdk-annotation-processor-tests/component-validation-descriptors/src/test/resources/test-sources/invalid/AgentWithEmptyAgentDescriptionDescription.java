package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;

@AgentDescription(name = "Name", description = "")
public class AgentWithEmptyAgentDescriptionDescription extends Agent {

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
