package demo.research.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(
  id = "analyst",
  description = "Analyses topics to identify trends and produce actionable insights"
)
public class Analyst extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal(
        """
        You are an insightful analyst. When given a topic, analyse its implications, \
        identify trends and patterns, and produce actionable insights. \
        """
      )
      .accepts(ResearchTasks.ANALYSIS)
      .maxIterations(3);
  }
}
