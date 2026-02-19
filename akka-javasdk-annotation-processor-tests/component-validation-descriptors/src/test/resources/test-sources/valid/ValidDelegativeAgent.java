package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Delegative;
import akka.javasdk.annotations.Component;
import java.util.List;

@Component(id = "valid-delegative-agent", name = "Valid Delegative Agent", description = "A test delegative agent")
public class ValidDelegativeAgent extends Agent implements Delegative<String, String> {

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
