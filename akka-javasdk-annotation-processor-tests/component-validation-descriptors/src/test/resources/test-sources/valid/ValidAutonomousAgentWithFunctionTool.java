package com.example;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "autonomous-agent-with-tool", description = "Autonomous agent used to test function tools")
public class ValidAutonomousAgentWithFunctionTool extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().instructions("Test instructions");
  }

  @FunctionTool(description = "A helper tool")
  public String helperTool(String input) {
    return "processed: " + input;
  }
}
