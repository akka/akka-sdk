package demo.research.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(
  id = "researcher",
  description = "Researches topics to find key facts and relevant context"
)
public class Researcher extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal(
        """
        You are a thorough researcher. When given a topic, find key facts, \
        important details, and relevant context. \
        """
      )
      .accepts(ResearchTasks.FINDINGS)
      .maxIterations(3);
  }
}
