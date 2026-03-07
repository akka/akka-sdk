package demo.consulting.application;

import static akka.javasdk.agent.autonomous.AutonomousAgent.agent;
import static akka.javasdk.agent.autonomous.AutonomousAgent.delegation;
import static akka.javasdk.agent.autonomous.AutonomousAgent.handoff;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.consulting.application.ConsultingTasks;

/**
 * Consulting coordinator — demonstrates delegation + handoff on the same agent.
 *
 * <p>Can delegate research subtasks in parallel (delegation), AND escalate to a senior consultant
 * when the problem is too complex (handoff).
 */
@Component(id = "consulting-coordinator")
public class ConsultingCoordinator extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(ConsultingTasks.ENGAGEMENT)
      .instructions(
        """
        You coordinate consulting engagements. For each client problem:

        1. Use assessProblem to understand the problem.
        2. Use checkComplexity to determine if this needs escalation.
        3. If COMPLEX: hand off to the senior consultant immediately. Include your \
           preliminary assessment in the handoff context.
        4. If STANDARD: delegate research to gather detailed findings, then \
           synthesise and complete the task with a ConsultingResult.

        You have two coordination capabilities:
        - Delegation: spawn researchers for focused subtasks (parallel research)
        - Handoff: escalate to a senior consultant (transfers the task entirely)\
        """
      )
      .tools(ConsultingTools.class)
      .capability(
        delegation(
          agent(Researcher.class, "Research a specific aspect of the client problem")
        )
      )
      .capability(
        handoff(
          agent(
            SeniorConsultant.class,
            "Senior consultant for complex problems involving regulatory, M&A, or" +
            " enterprise transformation"
          )
        )
      )
      .maxIterations(10);
  }
}
