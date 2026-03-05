package demo.research.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(id = "research-coordinator")
public class ResearchCoordinator extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal(
        """
        Produce comprehensive research briefs by synthesising findings \
        from multiple specialist perspectives. \
        """
      )
      .accepts(ResearchTasks.BRIEF)
      .canDelegateTo(Researcher.class, Analyst.class)
      .maxIterations(5);
  }
}
