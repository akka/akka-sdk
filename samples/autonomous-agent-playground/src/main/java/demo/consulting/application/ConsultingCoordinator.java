package demo.consulting.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

/**
 * Consulting coordinator — demonstrates delegation + handoff on the same agent.
 *
 * <p>Can delegate research subtasks to a researcher (delegation) and escalate complex problems to a
 * senior consultant (handoff).
 */
@Component(id = "consulting-coordinator")
public class ConsultingCoordinator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        """
        Deliver actionable consulting recommendations. Assess each client \
        problem, determine its complexity, and ensure it reaches the right \
        level of expertise for resolution. \
        """
      )
      .tools(new ConsultingTools())
      .capabilities(
        canAcceptTasks(ConsultingTasks.ENGAGEMENT)
          .canHandoffTo(SeniorConsultant.class)
          .canDelegateTo(ConsultingResearcher.class)
      );
  }
}
