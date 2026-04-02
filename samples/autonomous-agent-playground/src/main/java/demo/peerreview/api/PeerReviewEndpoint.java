package demo.peerreview.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.peerreview.application.ReviewModerator;
import demo.peerreview.application.ReviewTasks;
import demo.peerreview.application.ReviewTasks.ReviewResult;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/peerreview")
public class PeerReviewEndpoint {

  public record ReviewRequest(String document) {}

  public record ReviewResponse(String taskId) {}

  private final ComponentClient componentClient;

  public PeerReviewEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ReviewResponse create(ReviewRequest request) {
    var taskId = componentClient
      .forAutonomousAgent(ReviewModerator.class, UUID.randomUUID().toString())
      .runSingleTask(ReviewTasks.REVIEW.instructions(request.document()));
    return new ReviewResponse(taskId);
  }

  @Get("/{taskId}")
  public ReviewResult get(String taskId) {
    return componentClient.forTask(taskId).get(ReviewTasks.REVIEW).result();
  }
}
