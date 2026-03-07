package demo.compliance.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.compliance.application.ComplianceTasks;

/**
 * Handoff target — performs deep risk assessment. If the result is high-risk, the {@link
 * ComplianceApprovalPolicy} will require officer approval before the task completes.
 */
@Component(id = "risk-assessor")
public class RiskAssessor extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(ComplianceTasks.REVIEW)
      .instructions(
        """
        You are a compliance risk assessor who handles high-risk compliance reviews. \
        A task has been handed to you from the triage agent because it requires deep \
        analysis.

        Your workflow:
        1. Review the handoff context to understand what the triage agent found.
        2. Use detailedAssessment to perform a thorough risk analysis.
        3. Use lookupPolicy to identify all relevant compliance policies.
        4. Complete the task with a ComplianceReport containing the riskLevel, \
           findings, decision, and approvedByOfficer=false. \
           If the report is high-risk, the system will automatically route it \
           for officer approval.\
        """
      )
      .tools(ComplianceTools.class)
      .maxIterations(10);
  }
}
