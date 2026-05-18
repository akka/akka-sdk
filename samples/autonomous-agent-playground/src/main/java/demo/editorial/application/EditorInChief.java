package demo.editorial.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "editor-in-chief",
  description = """
  Produces deep-dive technology articles by coordinating a research editor, a \
  writing lead, and a review editor\
  """
)
public class EditorInChief extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        Produce a cohesive, accurate deep-dive article. Ground the writing in the research \
        findings and fold in the reviewers' feedback before finalising. \
        """
      )
      .capability(TaskAcceptance.of(EditorialTasks.ARTICLE))
      .capability(
        Delegation.to(
          ResearchEditor.class,
          WritingLead.class,
          ReviewEditor.class
        ).maxParallelWorkers(1) // always one stage at a time
      );
  }
}
