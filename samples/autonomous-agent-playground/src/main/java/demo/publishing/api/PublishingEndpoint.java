package demo.publishing.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.publishing.application.Article;
import demo.publishing.application.ContentAgent;
import demo.publishing.application.PublishingTasks;

/**
 * Content publishing with human-in-the-loop approval.
 *
 * <p>Usage:
 *
 * <pre>
 * # Create a publishing task
 * curl -X POST localhost:9000/publishing -H "Content-Type: application/json" \
 *   -d '{"topic": "AI in Healthcare"}'
 *
 * # Check status — will show WAITING_FOR_INPUT when approval is needed
 * curl localhost:9000/publishing/{id}
 *
 * # Approve the article
 * curl -X POST localhost:9000/publishing/{id}/approve
 *
 * # Or reject with feedback
 * curl -X POST localhost:9000/publishing/{id}/reject -H "Content-Type: application/json" \
 *   -d '{"reason": "Too short, needs more detail on regulatory aspects"}'
 *
 * # Check final result
 * curl localhost:9000/publishing/{id}
 * </pre>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/publishing")
public class PublishingEndpoint {

  public record CreatePublishing(String topic) {}

  public record PublishingResponse(String id) {}

  public record PublishingStatusResponse(
    String status,
    Article result,
    String pendingQuestion,
    String pendingDecisionId
  ) {}

  public record RejectRequest(String reason) {}

  private final ComponentClient componentClient;

  public PublishingEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public PublishingResponse create(CreatePublishing request) {
    var ref = componentClient
      .forAutonomousAgent(ContentAgent.class)
      .runSingleTask(
        PublishingTasks.ARTICLE.instructions(
          "Write and publish an article about: " + request.topic()
        )
      );

    return new PublishingResponse(ref.taskId());
  }

  @Get("/{id}")
  public PublishingStatusResponse get(String id) {
    var task = componentClient.forTask(PublishingTasks.ARTICLE.ref(id)).get();
    return new PublishingStatusResponse(
      task.status().name(),
      task.result(),
      task.pendingDecisionQuestion(),
      task.pendingDecisionId()
    );
  }

  @Post("/{id}/approve")
  public PublishingStatusResponse approve(String id) {
    var task = componentClient.forTask(PublishingTasks.ARTICLE.ref(id)).get();
    componentClient
      .forTask(PublishingTasks.ARTICLE.ref(id))
      .provideInput(task.pendingDecisionId(), "Approved. Proceed to publish the article.");

    return new PublishingStatusResponse("APPROVED", null, null, null);
  }

  @Post("/{id}/reject")
  public PublishingStatusResponse reject(String id, RejectRequest request) {
    var task = componentClient.forTask(PublishingTasks.ARTICLE.ref(id)).get();
    componentClient
      .forTask(PublishingTasks.ARTICLE.ref(id))
      .provideInput(
        task.pendingDecisionId(),
        "Rejected. Please revise the article based on this feedback: " + request.reason()
      );

    return new PublishingStatusResponse("REJECTED_WITH_FEEDBACK", null, null, null);
  }
}
