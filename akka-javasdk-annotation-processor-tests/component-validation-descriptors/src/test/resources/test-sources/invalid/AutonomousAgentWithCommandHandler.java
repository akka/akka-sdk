package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(id = "autonomous-agent-with-handler")
public class AutonomousAgentWithCommandHandler extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous().goal("Test goal");
  }

  // This should be rejected — AutonomousAgent must not have command handlers
  public Agent.Effect<String> query(String request) {
    throw new UnsupportedOperationException();
  }
}
