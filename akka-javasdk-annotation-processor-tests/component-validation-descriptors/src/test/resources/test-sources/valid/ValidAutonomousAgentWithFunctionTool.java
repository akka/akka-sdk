package com.example;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "autonomous-agent-with-tool")
public class ValidAutonomousAgentWithFunctionTool extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().goal("Test goal");
  }

  @FunctionTool(description = "A helper tool")
  public String helperTool(String input) {
    return "processed: " + input;
  }
}
