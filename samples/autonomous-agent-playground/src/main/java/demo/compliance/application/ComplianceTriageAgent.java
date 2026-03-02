package demo.compliance.application;

import static akka.javasdk.agent.autonomous.AutonomousAgent.agent;
import static akka.javasdk.agent.autonomous.AutonomousAgent.handoff;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.compliance.application.ComplianceTasks;

/**
 * Compliance triage — demonstrates handoff + decision points composition.
 *
 * <p>Classifies risk level and either handles directly (low risk) or hands off to the risk assessor
 * (high risk). The risk assessor then requests human approval — showing decision points composed
 * with handoff.
 */
@Component(id = "compliance-triage")
public class ComplianceTriageAgent extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(ComplianceTasks.REVIEW)
      .instructions(
        """
        You triage compliance review requests. Your workflow:

        1. Use scoreRisk to classify the risk level of the request.
        2. For LOW risk: use lookupPolicy, perform a brief review, and complete the \
           task directly with a ComplianceReport (approvedByOfficer=false).
        3. For MEDIUM risk: perform a standard review with lookupPolicy and complete \
           the task with a ComplianceReport.
        4. For HIGH risk: hand off to the risk assessor. Include your initial risk \
           assessment and relevant context in the handoff. The risk assessor will \
           perform deep analysis and request compliance officer approval.\
        """
      )
      .tools(ComplianceTools.class)
      .capability(
        handoff(
          agent(
            RiskAssessor.class,
            "Deep risk assessment for high-risk compliance issues, requests officer" +
            " approval"
          )
        )
      )
      .maxIterations(5);
  }
}
