/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.docreview.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "document-reviewer")
public class DocumentReviewer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        "Review documents for regulatory and compliance standards, " +
        "providing structured assessments with specific findings."
      )
      .canAcceptTasks(ReviewTasks.REVIEW)
      .maxIterationsPerTask(5);
  }
}
