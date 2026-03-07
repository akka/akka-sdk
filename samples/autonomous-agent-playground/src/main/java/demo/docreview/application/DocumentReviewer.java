package demo.docreview.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "document-reviewer")
public class DocumentReviewer extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(ReviewTasks.REVIEW)
      .instructions(
        "You are a document compliance reviewer. " +
        "Examine the attached document carefully and assess whether it meets " +
        "regulatory and compliance standards. " +
        "Report your findings as a structured result with an assessment summary, " +
        "a list of specific findings, and whether the document is compliant."
      )
      .maxIterations(5);
  }
}
