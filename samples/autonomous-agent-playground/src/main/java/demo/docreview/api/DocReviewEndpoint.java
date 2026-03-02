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

/**
 * Document review with text content attachment.
 *
 * <p>Demonstrates attaching document content to a task so the LLM reviews the actual document, not
 * just a description of it. For real-world use with PDFs or images, use {@code
 * PdfMessageContent.fromUrl()} or {@code ImageMessageContent.fromUrl()} with a {@code
 * ContentLoader} configured on the agent strategy.
 *
 * <p>Usage:
 *
 * <pre>
 * # Submit a document for compliance review
 * curl -X POST localhost:9000/docreview -H "Content-Type: application/json" \
 *   -d '{"document": "SERVICES AGREEMENT\n1. PARTIES: ...", "reviewInstructions": "Check for SOX compliance"}'
 *
 * # Check review status
 * curl localhost:9000/docreview/{id}
 * </pre>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/docreview")
public class DocReviewEndpoint {

  public record CreateReview(String document, String reviewInstructions) {}

  public record ReviewResponse(String id) {}

  public record ReviewStatusResponse(String status, ReviewResult result) {}

  private final ComponentClient componentClient;

  public DocReviewEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ReviewResponse create(CreateReview request) {
    var task = ReviewTasks.REVIEW.instructions(request.reviewInstructions()).attach(
      TextMessageContent.from(request.document())
    );

    var ref = componentClient.forAutonomousAgent(DocumentReviewer.class).runSingleTask(task);

    return new ReviewResponse(ref.taskId());
  }

  @Get("/{id}")
  public ReviewStatusResponse get(String id) {
    var task = componentClient.forTask(ReviewTasks.REVIEW.ref(id)).get();
    return new ReviewStatusResponse(task.status().name(), task.result());
  }
}
