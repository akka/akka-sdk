package com.example.shop;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

@AgentRole("support")
@Component(
    id = "support",
    name = "Support",
    description = "Answers \"customer\" questions\nabout orders, café \\ more")
public class SupportAgent extends Agent {

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
