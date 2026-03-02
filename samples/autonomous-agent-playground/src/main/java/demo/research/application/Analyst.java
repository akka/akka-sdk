package demo.research.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.research.application.ResearchTasks;

@Component(id = "analyst")
public class Analyst extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(ResearchTasks.BRIEF)
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
