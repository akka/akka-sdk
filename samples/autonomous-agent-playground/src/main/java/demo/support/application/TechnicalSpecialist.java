package demo.support.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(
  id = "technical-specialist",
  description = "Diagnoses and resolves technical problems, bugs, and service outages"
)
public class TechnicalSpecialist extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal("Diagnose and resolve technical issues for customers.")
      .accepts(SupportTasks.RESOLVE)
      .maxIterations(5);
  }
}
