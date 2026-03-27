package demo.research.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
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
      .capability(TaskAcceptance.of(ResearchTasks.BRIEF).maxIterationsPerTask(5))
      .capability(Delegation.to(Researcher.class, Analyst.class).maxParallelWorkers(3));
  }
}
