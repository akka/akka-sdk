package demo.peerreview.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Moderation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "review-moderator",
  description = "Coordinates peer review of documents through specialist reviewers"
)
public class ReviewModerator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal("Coordinate document peer review and synthesize reviewer findings.")
      .capability(TaskAcceptance.of(ReviewTasks.REVIEW))
      .capability(
        Moderation.of(TechnicalReviewer.class, StyleReviewer.class, ComplianceReviewer.class)
      );
  }
}
