/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.docreview.api;

import akka.javasdk.agent.MessageContent.TextMessageContent;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.docreview.application.DocumentReviewer;
import demo.docreview.application.ReviewResult;
import demo.docreview.application.ReviewTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/docreview")
public class DocReviewEndpoint {

  public record CreateReview(String document, String reviewInstructions) {}

  public record ReviewResponse(String id) {}

  private final ComponentClient componentClient;

  public DocReviewEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ReviewResponse create(CreateReview request) {
    // prettier-ignore
    var task = ReviewTasks.REVIEW
      .instructions(request.reviewInstructions())
      .attach(TextMessageContent.from(request.document()));

    var taskId = componentClient
      .forAutonomousAgent(DocumentReviewer.class, UUID.randomUUID().toString())
      .runSingleTask(task);

    return new ReviewResponse(taskId);
  }

  @Get("/{taskId}")
  public ReviewResult getReview(String taskId) {
    return componentClient.forTask(taskId).get(ReviewTasks.REVIEW).result();
  }
}
