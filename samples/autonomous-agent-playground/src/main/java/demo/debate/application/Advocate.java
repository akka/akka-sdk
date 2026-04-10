package demo.debate.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "advocate", description = "Argues in favor of a position in debates")
public class Advocate extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().goal("Present compelling arguments in favor of the assigned position.");
  }
}
