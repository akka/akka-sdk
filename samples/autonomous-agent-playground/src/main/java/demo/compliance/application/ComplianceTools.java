package demo.compliance.application;

import akka.javasdk.annotations.FunctionTool;

/** Tools for the compliance review sample. */
public class ComplianceTools {

  @FunctionTool(
    description = "Score the risk level of a compliance request. Returns LOW, MEDIUM, or HIGH."
  )
  public String scoreRisk(String description) {
    // Simulate: requests mentioning "financial" or "data breach" are high risk
    if (
      description.toLowerCase().contains("financial") ||
      description.toLowerCase().contains("data breach")
    ) {
      return (
        "HIGH RISK: This request involves sensitive financial data or security incidents. " +
        "Requires deep assessment by a risk specialist and compliance officer sign-off."
      );
    }
    if (
      description.toLowerCase().contains("policy") ||
      description.toLowerCase().contains("audit")
    ) {
      return (
        "MEDIUM RISK: Standard policy review or audit preparation. " +
        "Can be handled with standard assessment procedures."
      );
    }
    return "LOW RISK: Routine compliance check. Can be completed directly.";
  }

  @FunctionTool(description = "Look up relevant compliance policies for a given area.")
  public String lookupPolicy(String area) {
    return (
      "Policies for '" +
      area +
      "': Policy CP-101: Data handling requires encryption at rest and in transit. Policy" +
      " CP-205: Financial records must be retained for 7 years. Policy CP-310: All high-risk" +
      " findings require compliance officer review within 48 hours."
    );
  }

  @FunctionTool(description = "Perform a detailed risk assessment with specific findings.")
  public String detailedAssessment(String area, String riskLevel) {
    return (
      "Detailed assessment for '" +
      area +
      "' (risk: " +
      riskLevel +
      "): " +
      "Finding 1: Current controls partially address the identified risk. " +
      "Finding 2: Gap identified in monitoring procedures — recommend enhanced logging. " +
      "Finding 3: Third-party vendor contracts lack required compliance clauses. " +
      "Recommendation: Remediation plan needed within 30 days."
    );
  }
}
