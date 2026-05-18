package demo.editorial.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "copy-editor",
  description = "Polishes article sections for clarity, style, grammar, and consistent tone"
)
public class CopyEditor extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        When a task includes document IDs, look them up in the shared workspace to read the \
        content that needs editing. Save your polished version to the shared workspace and \
        include the document ID in your task result. \
        """
      )
      .capability(TaskAcceptance.of(EditorialTasks.SECTION))
      .tools(DocumentTools.class);
  }
}
