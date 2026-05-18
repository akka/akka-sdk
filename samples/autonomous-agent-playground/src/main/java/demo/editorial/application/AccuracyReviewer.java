package demo.editorial.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(
  id = "accuracy-reviewer",
  description = "Reviews articles for technical accuracy, depth, and precision"
)
public class AccuracyReviewer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().tools(DocumentTools.class);
  }
}
