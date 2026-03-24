package demo.publishing.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.publishing.application.ApprovalDecision;
import demo.publishing.application.ContentAgent;
import demo.publishing.application.PublishingAgent;
import demo.publishing.application.PublishingTasks;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/publishing")
public class PublishingEndpoint {

  public record PublishRequest(String topic) {}

  public record PublishingPipeline(
    String draftTaskId,
    String approvalTaskId,
    String publishTaskId
  ) {}

  public record ApproveRequest(String approvedBy, String comment) {}

  public record RejectRequest(String rejectedBy, String reason) {}

  public record TaskStatus(String status, Object result, String failureReason) {}

  private static final Logger log = LoggerFactory.getLogger(PublishingEndpoint.class);

  private final ComponentClient componentClient;

  public PublishingEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /**
   * Create a 3-task publishing pipeline: draft → approval → publish. The draft and publish tasks
   * are assigned to agents. The approval task is unassigned — it must be completed or failed by a
   * human via the approve/reject endpoints.
   */
  @Post
  public PublishingPipeline request(PublishRequest request) {
    // 1. Create draft task and assign to content agent
    var draftTaskId = componentClient
      .forAutonomousAgent(ContentAgent.class, UUID.randomUUID().toString())
      .runSingleTask(
        PublishingTasks.DRAFT.instructions("Write a blog post about: " + request.topic())
      );

    // 2. Create approval task (unassigned, depends on draft)
    var approvalTaskId = UUID.randomUUID().toString();
    componentClient
      .forTask(approvalTaskId)
      .create(
        PublishingTasks.APPROVAL.instructions(
          "Review the draft and approve or reject for publishing."
        ).dependsOn(draftTaskId)
      );

    // 3. Create publish task assigned to publishing agent (depends on approval)
    var publishTaskId = componentClient
      .forAutonomousAgent(PublishingAgent.class, UUID.randomUUID().toString())
      .runSingleTask(
        PublishingTasks.PUBLISH.instructions("Publish the approved post.").dependsOn(
          approvalTaskId
        )
      );

    // Watch pipeline progress via task result subscriptions
    componentClient
      .forTask(draftTaskId)
      .resultAsync(PublishingTasks.DRAFT)
      .thenAccept(draft -> log.info("Draft completed: '{}'", draft.title()))
      .exceptionally(ex -> {
        log.warn("Draft failed: {}", ex.getMessage());
        return null;
      });

    componentClient
      .forTask(approvalTaskId)
      .resultAsync(PublishingTasks.APPROVAL)
      .thenAccept(
        approval -> log.info("Approved by {}: {}", approval.approvedBy(), approval.comment())
      )
      .exceptionally(ex -> {
        log.warn("Approval rejected: {}", ex.getMessage());
        return null;
      });

    componentClient
      .forTask(publishTaskId)
      .resultAsync(PublishingTasks.PUBLISH)
      .thenAccept(pub -> log.info("Published at {}", pub.url()))
      .exceptionally(ex -> {
        log.warn("Publishing failed: {}", ex.getMessage());
        return null;
      });

    return new PublishingPipeline(draftTaskId, approvalTaskId, publishTaskId);
  }

  /** Get the current status of the draft task, so a human can review before approving. */
  @Get("/draft/{taskId}")
  public TaskStatus getDraft(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(PublishingTasks.DRAFT);
    return new TaskStatus(
      snapshot.status().name(),
      snapshot.result(),
      snapshot.failureReason()
    );
  }

  /** Human approves the draft — assigns and completes the approval task. */
  @Post("/approve/{approvalTaskId}")
  public String approve(String approvalTaskId, ApproveRequest request) {
    var taskClient = componentClient.forTask(approvalTaskId);
    taskClient.assign(request.approvedBy());
    taskClient.complete(new ApprovalDecision(request.approvedBy(), request.comment()));
    return "Approved";
  }

  /** Human rejects the draft — assigns and fails the approval task. */
  @Post("/reject/{approvalTaskId}")
  public String reject(String approvalTaskId, RejectRequest request) {
    var taskClient = componentClient.forTask(approvalTaskId);
    taskClient.assign(request.rejectedBy());
    taskClient.fail(request.reason());
    return "Rejected";
  }

  /** Check the status of the final publish task. */
  @Get("/status/{taskId}")
  public TaskStatus getStatus(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(PublishingTasks.PUBLISH);
    return new TaskStatus(
      snapshot.status().name(),
      snapshot.result(),
      snapshot.failureReason()
    );
  }
}
