package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Delegative;
import akka.javasdk.annotations.Component;
import java.util.List;

@Component(id = "delegative-agent-with-handler")
public class DelegativeAgentWithCommandHandler extends Agent implements Delegative<String, String> {

  // Delegative agents must not have command handlers - this should fail validation
  public Effect<String> query(String request) {
    return effects().reply("response");
  }

  @Override
  public String instructions() {
    return "Some instructions";
  }

  @Override
  public List<Delegative.Delegation> delegations() {
    return List.of();
  }

  @Override
  public Class<String> resultType() {
    return String.class;
  }
}
