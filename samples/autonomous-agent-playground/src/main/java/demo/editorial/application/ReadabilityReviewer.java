package demo.editorial.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(
  id = "readability-reviewer",
  description = """
  Reviews articles for clarity, structure, and readability for a broad \
  professional audience\
  """
)
public class ReadabilityReviewer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().tools(DocumentTools.class);
  }
}
