package demo.peerreview.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "compliance-reviewer", description = "Reviews documents for compliance")
public class ComplianceReviewer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().goal("Review documents for regulatory compliance and policy adherence.");
  }
}
