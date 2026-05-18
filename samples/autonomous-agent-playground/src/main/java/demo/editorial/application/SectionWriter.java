package demo.editorial.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "section-writer",
  description = """
  Writes article sections from research findings. Makes technical content \
  accessible to a professional audience\
  """
)
public class SectionWriter extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        When a task includes document IDs, look them up in the shared workspace to get the \
        full research content. Use this to write your section. Save the completed section to \
        the shared workspace and include the document ID in your task result. \
        """
      )
      .capability(TaskAcceptance.of(EditorialTasks.SECTION))
      .tools(DocumentTools.class);
  }
}
