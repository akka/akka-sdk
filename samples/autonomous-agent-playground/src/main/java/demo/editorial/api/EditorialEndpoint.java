package demo.editorial.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.editorial.application.ChiefEditor;
import demo.editorial.application.Publication;

/**
 * Editorial workflow with team coordination and human approval.
 *
 * <p>Usage:
 *
 * <pre>
 * # Start a publication
 * curl -X POST localhost:9000/editorial -H "Content-Type: application/json" \
 *   -d '{"topic": "The Future of Remote Work"}'
 *
 * # Check status (will show WAITING_FOR_INPUT when approval is needed)
 * curl localhost:9000/editorial/{id}
 *
 * # Approve the publication
 * curl -X POST localhost:9000/editorial/{id}/approve
 *
 * # Or reject with feedback
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
    String rawResult,
    String pendingQuestion,
    String pendingDecisionId
  ) {}

  public record RejectRequest(String reason) {}

  private final ComponentClient componentClient;

  public EditorialEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public EditorialResponse create(CreateEditorial request) {
    var taskId = componentClient
      .forAutonomousAgent(ChiefEditor.class)
      .runSingleTask("Produce a publication about: " + request.topic(), Publication.class);

    return new EditorialResponse(taskId);
  }

  @Get("/{id}")
  public EditorialStatusResponse get(String id) {
    var task = componentClient.forTask(id, Publication.class);
    var state = task.getState();
    return new EditorialStatusResponse(
      state.status().name(),
      task.getResult(),
      state.result(),
      state.pendingDecisionQuestion(),
      state.pendingDecisionId()
    );
  }

  @Post("/{id}/approve")
  public EditorialStatusResponse approve(String id) {
    var task = componentClient.forTask(id, Publication.class);
    var state = task.getState();
    task.provideInput(state.pendingDecisionId(), "Approved. Publish the article.");
    return new EditorialStatusResponse("APPROVED", null, null, null, null);
  }

  @Post("/{id}/reject")
  public EditorialStatusResponse reject(String id, RejectRequest request) {
    var task = componentClient.forTask(id, Publication.class);
    var state = task.getState();
    task.provideInput(
      state.pendingDecisionId(),
      "Rejected. Revise based on this feedback: " + request.reason()
    );
    return new EditorialStatusResponse("REJECTED_WITH_FEEDBACK", null, null, null, null);
  }
}
