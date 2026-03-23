package demo.research.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(
  id = "researcher",
  description = "Researches topics to find key facts and relevant context"
)
public class Researcher extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        """
        You are a thorough researcher. When given a topic, find key facts, \
        important details, and relevant context. \
        """
      )
      .canAcceptTasks(ResearchTasks.FINDINGS)
      .maxIterationsPerTask(3);
  }
}
