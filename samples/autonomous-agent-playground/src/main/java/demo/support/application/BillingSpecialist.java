package demo.support.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "billing-specialist")
public class BillingSpecialist extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You are a billing specialist. Review the customer's billing issue using \
        the context provided by the triage agent. Look up customer details, \
        check the knowledge base, and resolve the dispute. \
        Complete the task with a resolution including what was found \
        and what action was taken.\
        """
      )
      .tools(SupportTools.class)
      .maxIterations(5);
  }
}
