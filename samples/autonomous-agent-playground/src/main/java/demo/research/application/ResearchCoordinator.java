package demo.research.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

// tag::class[]
@Component(
  id = "research-coordinator",
  description = """
  Produces comprehensive research briefs by synthesizing findings \
  from multiple specialist perspectives\
  """
)
public class ResearchCoordinator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(ResearchTasks.BRIEF).maxIterationsPerTask(5))
      .capability(Delegation.to(Researcher.class, Analyst.class).maxParallelWorkers(3));
  }
}
// end::class[]
