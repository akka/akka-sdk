package demo.editorial.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "reporter",
  description = """
  Researches a specific angle of a technology topic. Finds key facts, technical \
  details, and context, citing sources where possible. Saves findings to the shared workspace\
  """
)
public class Reporter extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        Save your complete findings to the shared workspace — this is how other agents \
        access your work. Use a descriptive title and a one-sentence summary. Include the \
        returned document ID in your task result so it can be referenced later. \
        """
      )
      .capability(TaskAcceptance.of(EditorialTasks.FINDINGS))
      .tools(DocumentTools.class);
  }
}
