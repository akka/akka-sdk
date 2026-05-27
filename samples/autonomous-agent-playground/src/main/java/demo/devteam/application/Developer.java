package demo.devteam.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

// tag::class[]
@Component(id = "developer", description = "Implements features with clean, tested code")
public class Developer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        Coordinate with teammates when your work depends on or affects \
        their tasks — agree on shared contracts before implementing. \
        """
      )
      .capability(TaskAcceptance.of(DeveloperTasks.IMPLEMENT))
      .tools(new CodeTools());
  }
}
// end::class[]
