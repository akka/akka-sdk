package demo.support.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

// tag::class[]
@Component(
  id = "triage-agent",
  description = """
  Classifies customer support requests and routes them to the appropriate \
  specialist via handoff\
  """
)
public class TriageAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(
        TaskAcceptance.of(SupportTasks.RESOLVE)
          .maxIterationsPerTask(3)
          .canHandoffTo(BillingSpecialist.class, TechnicalSpecialist.class)
      );
  }
}
// end::class[]
