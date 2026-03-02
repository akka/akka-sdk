package demo.compliance.application;

import akka.javasdk.agent.task.PolicyResult;
import akka.javasdk.agent.task.TaskCompletionContext;
import akka.javasdk.agent.task.TaskPolicy;
import demo.compliance.application.ComplianceReport;

/**
 * High-risk compliance reports require officer approval. Low-risk reports are allowed through
 * automatically.
 */
public class ComplianceApprovalPolicy implements TaskPolicy<ComplianceReport> {

  @Override
  public PolicyResult onCompletion(TaskCompletionContext<ComplianceReport> context) {
    var report = context.result();
    if ("high".equalsIgnoreCase(report.riskLevel())) {
      return PolicyResult.requireApproval(
        "High-risk compliance report requires officer sign-off"
      );
    }
    return PolicyResult.allow();
  }
}
