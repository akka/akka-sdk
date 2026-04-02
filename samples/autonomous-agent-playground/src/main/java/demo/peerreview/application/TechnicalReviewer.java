package demo.peerreview.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(
  id = "technical-reviewer",
  description = "Reviews documents for technical accuracy"
)
public class TechnicalReviewer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal("Review documents for technical accuracy, correctness, and completeness.");
  }
}
