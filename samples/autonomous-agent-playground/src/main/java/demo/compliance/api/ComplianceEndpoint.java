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
 * Compliance review pipeline with handoff and policy-driven approval.
 *
 * <p>The {@link demo.compliance.application.ComplianceApprovalPolicy} requires officer sign-off for
 * high-risk reports. Low-risk reports complete automatically.
 *
 * <p>Usage:
 *
 * <pre>
 * # Submit a low-risk review (completes automatically)
 * curl -X POST localhost:9000/compliance -H "Content-Type: application/json" \
 *   -d '{"request": "Annual policy review for office safety procedures"}'
 *
 * # Submit a high-risk review (triggers handoff + policy approval)
 * curl -X POST localhost:9000/compliance -H "Content-Type: application/json" \
 *   -d '{"request": "Data breach investigation for financial records system"}'
 *
 * # Check status (will show AWAITING_APPROVAL when officer sign-off is needed)
 * curl localhost:9000/compliance/{id}
 *
 * # Approve
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
    String approvalReason
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
      task.approvalReason()
    );
  }

  @Post("/{id}/approve")
  public ComplianceStatusResponse approve(String id) {
    componentClient.forTask(ComplianceTasks.REVIEW.ref(id)).approve();
    return new ComplianceStatusResponse("APPROVED", null, null);
  }

  @Post("/{id}/reject")
  public ComplianceStatusResponse reject(String id, RejectRequest request) {
    componentClient.forTask(ComplianceTasks.REVIEW.ref(id)).reject(request.reason());
    return new ComplianceStatusResponse("REJECTED", null, null);
  }
}
