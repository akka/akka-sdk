package demo.research.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(
  id = "analyst",
  description = "Analyses topics to identify trends and produce actionable insights"
)
public class Analyst extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        """
        You are an insightful analyst. When given a topic, analyse its implications, \
        identify trends and patterns, and produce actionable insights. \
        """
      )
      .canAcceptTask(ResearchTasks.ANALYSIS, task -> task
        .maxIterationsPerTask(3));
  }
}
