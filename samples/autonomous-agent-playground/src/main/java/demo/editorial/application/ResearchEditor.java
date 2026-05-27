package demo.editorial.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "research-editor",
  description = """
  Commissions research on a technology topic from multiple angles by delegating \
  to reporters, and returns a consolidated research digest\
  """
)
public class ResearchEditor extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(EditorialTasks.RESEARCH))
      .capability(Delegation.to(Reporter.class).maxParallelWorkers(2));
  }
}
