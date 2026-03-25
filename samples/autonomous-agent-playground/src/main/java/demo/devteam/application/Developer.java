package demo.devteam.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "developer", description = "Implements features with clean, tested code")
public class Developer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal("Implement features with clean, tested code.")
      .capability(TaskAcceptance.of(DeveloperTasks.IMPLEMENT))
      .tools(new CodeTools());
  }
}
