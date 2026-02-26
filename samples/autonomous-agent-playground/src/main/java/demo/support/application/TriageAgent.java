package demo.support.application;

import static akka.javasdk.agent.autonomous.AutonomousAgent.agent;
import static akka.javasdk.agent.autonomous.AutonomousAgent.handoff;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "triage-agent")
public class TriageAgent extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You are a customer support triage agent. Read the support request, \
        classify it, and determine if you can handle it directly. \
        If the issue is billing-related, hand it off to the billing specialist. \
        If the issue is technical, hand it off to the technical specialist. \
        For simple requests you can handle yourself, complete the task directly.\
        """
      )
      .tools(SupportTools.class)
      .capability(
        handoff(
          agent(
            BillingSpecialist.class,
            "Handles billing disputes, payment issues, and invoices"
          ),
          agent(
            TechnicalSpecialist.class,
            "Handles technical problems, bugs, and service outages"
          )
        )
      )
      .maxIterations(5);
  }
}
