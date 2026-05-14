package demo.dynamic.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

/**
 * A generic agent with no static instructions or capabilities. Configured dynamically at runtime
 * via AgentSetup before assigning tasks.
 */
// tag::class[]
@Component(
  id = "dynamic-agent",
  description = "Generic agent configured dynamically per request via AgentSetup"
)
public class DynamicAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define();
  }
}
// end::class[]
