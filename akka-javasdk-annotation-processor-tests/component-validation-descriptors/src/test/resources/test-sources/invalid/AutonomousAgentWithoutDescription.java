package com.example;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.annotations.Component;

@Component(id = "autonomous-agent-no-description")
public class AutonomousAgentWithoutDescription extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().instructions("Test instructions");
  }
}
