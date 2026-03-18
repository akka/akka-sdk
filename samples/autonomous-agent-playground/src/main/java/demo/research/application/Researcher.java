package demo.research.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.research.application.ResearchTasks;

@Component(id = "researcher")
public class Researcher extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(ResearchTasks.BRIEF)
      .instructions(
        """
        You are a thorough researcher. When given a topic, find key facts, \
        important details, and relevant context. Complete your task with a \
        clear summary of your findings.\
        """
      )
      .maxIterations(5);
  }
}
