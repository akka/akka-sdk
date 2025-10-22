package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "agent-no-handler")
public class AgentWithNoCommandHandler extends Agent {
  // No method returning Agent.Effect - should fail validation

  public void someMethod() {
    // Invalid: returns void instead of Effect
  }
}
