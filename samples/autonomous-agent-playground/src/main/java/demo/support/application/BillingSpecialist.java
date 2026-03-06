package demo.support.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(
  id = "billing-specialist",
  description = "Resolves billing disputes, payment issues, and invoice queries"
)
public class BillingSpecialist extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal("Resolve billing and payment issues for customers.")
      .accepts(SupportTasks.RESOLVE)
      .maxIterations(5);
  }
}
