package demo.consulting.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(id = "consulting-coordinator")
public class ConsultingCoordinator extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
        .goal("Deliver actionable consulting recommendations. Assess each client " +
            "problem, determine its complexity, and ensure it reaches the right " +
            "level of expertise for resolution. " +
            "For standard problems: delegate research to the ConsultingResearcher, " +
            "wait for findings, then synthesise a recommendation. " +
            "For complex problems (regulatory, M&A, compliance): hand off the " +
            "entire engagement to the SeniorConsultant.")
        .accepts(ConsultingTasks.ENGAGEMENT)
        .tools(new ConsultingTools())
        .canDelegateTo(ConsultingResearcher.class)
        .canHandoffTo(SeniorConsultant.class)
        .maxIterations(10);
  }
}
