package demo.debate.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "critic", description = "Argues against a position in debates")
public class Critic extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        "Present compelling counterarguments and identify weaknesses in the opposing position."
      );
  }
}
