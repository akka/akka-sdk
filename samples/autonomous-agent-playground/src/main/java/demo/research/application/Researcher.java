package demo.research.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "researcher")
public class Researcher extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
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
