package demo.consulting.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

/**
 * Consulting coordinator — demonstrates delegation + handoff on the same agent.
 *
 * <p>Can delegate research subtasks to a researcher (delegation), escalate complex problems to a
 * senior consultant (handoff), and delegate fact-checking to a request-based agent.
 */
// tag::class[]
@Component(
  id = "consulting-coordinator",
  description = """
    Delivers actionable consulting recommendations by assessing \
    problem complexity and routing to the right expertise level\
    """
)
public class ConsultingCoordinator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .tools(new ConsultingTools())
      .capability(
        TaskAcceptance.of(ConsultingTasks.ENGAGEMENT).canHandoffTo(SeniorConsultant.class)
      )
      .capability(Delegation.to(ConsultingResearcher.class).maxParallelWorkers(2))
      .capability(Delegation.to(FactCheckAgent.class));
  }
}
// end::class[]
