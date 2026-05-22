package demo.peerreview.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import demo.peerreview.application.ReviewModerator;
import demo.peerreview.application.ReviewTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/peerreview")
public class PeerReviewEndpoint extends AbstractHttpEndpoint {

  public record ReviewRequest(String document) {}

  public record ReviewResponse(String taskId, String runId, String agentComponentId) {}

  private final ComponentClient componentClient;

  public PeerReviewEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ReviewResponse create(ReviewRequest request) {
    var agentInstanceId = requestContext()
      .queryParams()
      .getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    var taskId = componentClient
      .forAutonomousAgent(ReviewModerator.class, agentInstanceId)
      .runSingleTask(ReviewTasks.REVIEW.instructions(request.document()));
    return new ReviewResponse(taskId, agentInstanceId, "review-moderator");
  }

  @Get("/{taskId}")
  public HttpResponse get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(ReviewTasks.REVIEW);
    return snapshot
      .result()
      .<HttpResponse>map(HttpResponses::ok)
      .orElseGet(() -> HttpResponses.accepted(snapshot.status().name()));
  }
}
