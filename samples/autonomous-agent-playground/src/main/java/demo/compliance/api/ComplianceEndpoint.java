package demo.compliance.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.compliance.application.ComplianceReport;
import demo.compliance.application.ComplianceTasks;
import demo.compliance.application.ComplianceTriageAgent;

/**
 * Compliance review pipeline with handoff and human approval.
 *
 * <p>Usage:
 *
 * <pre>
 * # Submit a low-risk review (handled directly)
 * curl -X POST localhost:9000/compliance -H "Content-Type: application/json" \
 *   -d '{"request": "Annual policy review for office safety procedures"}'
 *
 * # Submit a high-risk review (triggers handoff + approval)
 * curl -X POST localhost:9000/compliance -H "Content-Type: application/json" \
 *   -d '{"request": "Data breach investigation for financial records system"}'
 *
 * # Check status (will show WAITING_FOR_INPUT when officer approval is needed)
 * curl localhost:9000/compliance/{id}
 *
 * # Approve (when status shows WAITING_FOR_INPUT)
 * curl -X POST localhost:9000/compliance/{id}/approve
 *
 * # Or reject
 * curl -X POST localhost:9000/compliance/{id}/reject -H "Content-Type: application/json" \
 *   -d '{"reason": "Need additional evidence for finding #2"}'
 * </pre>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/compliance")
public class ComplianceEndpoint {

  public record CreateCompliance(String request) {}

  public record ComplianceResponse(String id) {}

  public record ComplianceStatusResponse(
    String status,
    ComplianceReport result,
    String pendingQuestion,
    String pendingDecisionId
  ) {}

  public record RejectRequest(String reason) {}

  private final ComponentClient componentClient;

  public ComplianceEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ComplianceResponse create(CreateCompliance request) {
    var ref = componentClient
      .forAutonomousAgent(ComplianceTriageAgent.class)
      .runSingleTask(
        ComplianceTasks.REVIEW.instructions("Compliance review: " + request.request())
      );

    return new ComplianceResponse(ref.taskId());
  }

  @Get("/{id}")
  public ComplianceStatusResponse get(String id) {
    var task = componentClient.forTask(ComplianceTasks.REVIEW.ref(id)).get();
    return new ComplianceStatusResponse(
      task.status().name(),
      task.result(),
      task.pendingDecisionQuestion(),
      task.pendingDecisionId()
    );
  }

  @Post("/{id}/approve")
  public ComplianceStatusResponse approve(String id) {
    var task = componentClient.forTask(ComplianceTasks.REVIEW.ref(id)).get();
    componentClient
      .forTask(ComplianceTasks.REVIEW.ref(id))
      .provideInput(task.pendingDecisionId(), "Approved by compliance officer. Proceed.");
    return new ComplianceStatusResponse("APPROVED", null, null, null);
  }

  @Post("/{id}/reject")
  public ComplianceStatusResponse reject(String id, RejectRequest request) {
    var task = componentClient.forTask(ComplianceTasks.REVIEW.ref(id)).get();
    componentClient
      .forTask(ComplianceTasks.REVIEW.ref(id))
      .provideInput(
        task.pendingDecisionId(),
        "Rejected by compliance officer: " + request.reason()
      );
    return new ComplianceStatusResponse("REJECTED_WITH_FEEDBACK", null, null, null);
  }
}
