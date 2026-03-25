package demo.support.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(
  id = "billing-specialist",
  description = "Resolves billing disputes, payment issues, and invoice queries"
)
public class BillingSpecialist extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal("Resolve billing and payment issues for customers.")
      .capabilities(canAcceptTasks(SupportTasks.RESOLVE).maxIterationsPerTask(5));
  }
}
