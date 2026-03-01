package demo.docreview.application;

import akka.javasdk.agent.task.Task;

public class ReviewTasks {

  public static final Task<ReviewResult> REVIEW = Task.of(
    "Review a document for compliance",
    ReviewResult.class
  );
}
