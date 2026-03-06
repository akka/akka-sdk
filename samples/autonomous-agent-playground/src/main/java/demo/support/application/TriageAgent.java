package demo.support.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(id = "triage-agent")
public class TriageAgent extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal(
        """
        Classify customer support requests and ensure they are resolved \
        by the appropriate specialist. \
        """
      )
      .accepts(SupportTasks.RESOLVE)
      .canHandoffTo(BillingSpecialist.class, TechnicalSpecialist.class)
      .maxIterations(3);
  }
}
