package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentTeam;
import akka.javasdk.annotations.Component;
import java.util.List;

@Component(id = "delegative-agent-with-handler")
public class AgentTeamAgentWithCommandHandler extends Agent implements AgentTeam<String, String> {

  // AgentTeam agents must not have command handlers - this should fail validation
  public Effect<String> query(String request) {
    return effects().reply("response");
  }

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
