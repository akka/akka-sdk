package demo.peerreview.application;

import akka.javasdk.agent.task.Task;
import java.util.List;

public class ReviewTasks {

  public record ReviewResult(
    String document,
    String assessment,
    List<String> reviewerFindings
  ) {}

  public static final Task<ReviewResult> REVIEW = Task.define("Review")
    .description(
      "Coordinate peer review of a document by technical, style, and compliance reviewers."
    )
    .resultConformsTo(ReviewResult.class);
}
