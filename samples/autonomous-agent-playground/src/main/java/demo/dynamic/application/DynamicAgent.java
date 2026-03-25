package demo.dynamic.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

/**
 * A generic agent with no static goal or capabilities. Configured dynamically at runtime via
 * AgentSetup before assigning tasks.
 */
@Component(id = "dynamic-agent")
public class DynamicAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define();
  }
}
