package demo.editorial.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Moderation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "review-editor",
  description = """
  Reviews an article draft for technical accuracy and readability by moderating a \
  panel of an accuracy reviewer and a readability reviewer, and returns consolidated review notes\
  """
)
public class ReviewEditor extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(EditorialTasks.REVIEW))
      .capability(Moderation.of(AccuracyReviewer.class, ReadabilityReviewer.class))
      .tools(DocumentTools.class);
  }
}
