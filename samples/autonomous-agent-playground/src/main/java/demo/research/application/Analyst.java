package demo.research.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "analyst")
public class Analyst extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You are an insightful analyst. When given a topic, analyse its implications, \
        identify trends and patterns, and produce actionable insights. Complete \
        your task with a clear analysis.\
        """
      )
      .maxIterations(5);
  }
}
