package com.example;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(id = "valid-autonomous-agent", name = "Valid Autonomous Agent", description = "A test autonomous agent")
public class ValidAutonomousAgent extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous().goal("Test goal");
  }
}
