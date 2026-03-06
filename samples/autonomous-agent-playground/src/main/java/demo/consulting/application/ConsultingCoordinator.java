package demo.consulting.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
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
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal(
        """
        Deliver actionable consulting recommendations. Assess each client \
        problem, determine its complexity, and ensure it reaches the right \
        level of expertise for resolution. \
        """
      )
      .accepts(ConsultingTasks.ENGAGEMENT)
      .tools(new ConsultingTools())
      .canDelegateTo(ConsultingResearcher.class)
      .canHandoffTo(SeniorConsultant.class)
      .maxIterations(10);
  }
}
