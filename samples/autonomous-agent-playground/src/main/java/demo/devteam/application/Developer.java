package demo.devteam.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "developer")
public class Developer extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You are a developer. Claim tasks, implement them using your code tools, \
        and mark them complete. Continue until no tasks remain.\
        """
      )
      .tools(CodeTools.class)
      .maxIterations(30);
  }
}
