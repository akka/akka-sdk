package demo.compliance.application;

import static akka.javasdk.agent.autonomous.AutonomousAgent.externalInput;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.compliance.application.ComplianceTasks;

/**
 * Handoff target — performs deep risk assessment and requests compliance officer approval.
 *
 * <p>Demonstrates external input within a handoff chain: this agent was handed a task from the
 * triage agent, and it requests sign-off before completing.
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
        4. Use requestDecision to present your findings to the compliance officer \
           for approval. Include your assessment, findings, and recommendation.
        5. Based on the officer's response, complete the task with a ComplianceReport.\
        """
      )
      .tools(ComplianceTools.class)
      .capability(externalInput())
      .maxIterations(10);
  }
}
