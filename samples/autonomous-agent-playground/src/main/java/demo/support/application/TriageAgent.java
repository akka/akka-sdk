package demo.support.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.annotations.Component;

@Component(id = "triage-agent")
public class TriageAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        """
        Classify customer support requests and ensure they are resolved \
        by the appropriate specialist. \
        """
      )
      .capabilities(
          canAcceptTasks(SupportTasks.RESOLVE)
              .maxIterationsPerTask(3)
              .canHandoffTo(BillingSpecialist.class, TechnicalSpecialist.class));
  }
}
