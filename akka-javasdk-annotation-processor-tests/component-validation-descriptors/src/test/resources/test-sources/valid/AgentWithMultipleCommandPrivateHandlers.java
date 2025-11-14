package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "agent-multiple-handlers")
public class AgentWithMultipleCommandPrivateHandlers extends Agent {
  // Multiple methods returning Agent.Effect - should fail validation

  public Effect<String> query1(String request) {
    return effects().reply("response1");
  }

  private Effect<String> query2(String request) {
    return effects().reply("response2");
  }

  private Effect<String> query3(String request) {
    return effects().reply("response3");
  }
}
