/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.docreview.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(id = "document-reviewer")
public class DocumentReviewer extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal(
        "Review documents for regulatory and compliance standards, " +
        "providing structured assessments with specific findings."
      )
      .accepts(ReviewTasks.REVIEW)
      .maxIterations(5);
  }
}
