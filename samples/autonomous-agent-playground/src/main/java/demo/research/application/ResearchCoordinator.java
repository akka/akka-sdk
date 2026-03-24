package demo.research.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "research-coordinator")
public class ResearchCoordinator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        """
        Produce comprehensive research briefs by synthesising findings \
        from multiple specialist perspectives. \
        """
      )
      .canAcceptTask(ResearchTasks.BRIEF, task -> task
        .maxIterationsPerTask(5))
      .canDelegateTo(Researcher.class, delegation -> delegation
        .maxParallelWorkers(3))
      .canDelegateTo(Analyst.class, delegation -> delegation
        .maxParallelWorkers(3));
  }
}
