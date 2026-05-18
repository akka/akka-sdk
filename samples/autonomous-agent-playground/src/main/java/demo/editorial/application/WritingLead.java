package demo.editorial.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.agent.autonomous.capability.TeamLeadership;
import akka.javasdk.agent.autonomous.capability.TeamLeadership.TeamMember;
import akka.javasdk.annotations.Component;

@Component(
  id = "writing-lead",
  description = """
  Turns research findings into a structured article draft by leading a team of \
  a section writer and a copy editor\
  """
)
public class WritingLead extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        Break the article into a few focused sections and have the team write and \
        copy-edit each section, then assemble them into the finished draft. \
        """
      )
      .capability(TaskAcceptance.of(EditorialTasks.DRAFT))
      .capability(
        TeamLeadership.of(
          TeamMember.of(SectionWriter.class).maxInstances(1),
          TeamMember.of(CopyEditor.class).maxInstances(1)
        )
      )
      .tools(DocumentTools.class);
  }
}
