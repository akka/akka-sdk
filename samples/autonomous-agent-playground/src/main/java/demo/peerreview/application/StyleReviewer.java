package demo.peerreview.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "style-reviewer", description = "Reviews documents for clarity and style")
public class StyleReviewer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().goal("Review documents for clarity, readability, and consistent style.");
  }
}
