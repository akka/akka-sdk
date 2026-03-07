package demo.editorial.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.editorial.application.ChiefEditor;
import demo.editorial.application.EditorialTasks;
import demo.editorial.application.Publication;

/**
 * Editorial workflow with team coordination and policy-driven approval.
 *
 * <p>The {@link demo.editorial.application.PublicationApprovalPolicy} requires editorial approval
 * before any publication can be finalised.
 *
 * <p>Usage:
 *
 * <pre>
 * # Start a publication
 * curl -X POST localhost:9000/editorial -H "Content-Type: application/json" \
 *   -d '{"topic": "The Future of Remote Work"}'
 *
 * # Check status (will show AWAITING_APPROVAL when ready for review)
 * curl localhost:9000/editorial/{id}
 *
 * # Approve the publication
 * curl -X POST localhost:9000/editorial/{id}/approve
 *
 * # Or reject (task fails)
 * curl -X POST localhost:9000/editorial/{id}/reject -H "Content-Type: application/json" \
 *   -d '{"reason": "Needs stronger conclusion"}'
 * </pre>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/editorial")
public class EditorialEndpoint {

  public record CreateEditorial(String topic) {}

  public record EditorialResponse(String id) {}

  public record EditorialStatusResponse(
    String status,
    Publication result,
    String approvalReason
  ) {}

  public record RejectRequest(String reason) {}

  private final ComponentClient componentClient;

  public EditorialEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public EditorialResponse create(CreateEditorial request) {
    var ref = componentClient
      .forAutonomousAgent(ChiefEditor.class)
      .runSingleTask(
        EditorialTasks.PUBLICATION.instructions(
          "Produce a publication about: " + request.topic()
        )
      );

    return new EditorialResponse(ref.taskId());
  }

  @Get("/{id}")
  public EditorialStatusResponse get(String id) {
    var task = componentClient.forTask(EditorialTasks.PUBLICATION.ref(id)).get();
    return new EditorialStatusResponse(
      task.status().name(),
      task.result(),
      task.approvalReason()
    );
  }

  @Post("/{id}/approve")
  public EditorialStatusResponse approve(String id) {
    componentClient.forTask(EditorialTasks.PUBLICATION.ref(id)).approve();
    return new EditorialStatusResponse("APPROVED", null, null);
  }

  @Post("/{id}/reject")
  public EditorialStatusResponse reject(String id, RejectRequest request) {
    componentClient.forTask(EditorialTasks.PUBLICATION.ref(id)).reject(request.reason());
    return new EditorialStatusResponse("REJECTED", null, null);
  }
}
