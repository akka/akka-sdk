package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentTeam;
import akka.javasdk.annotations.Component;
import java.util.List;

@Component(id = "valid-delegative-agent", name = "Valid AgentTeam Agent", description = "A test delegative agent")
public class ValidAgentTeamAgent extends Agent implements AgentTeam<String, String> {

  @Override
  public String instructions() {
    return "Some instructions";
  }

  @Override
  public List<AgentTeam.Delegation> delegations() {
    return List.of();
  }

  @Override
  public Class<String> resultType() {
    return String.class;
  }
}
