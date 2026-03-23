package demo.support.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(
  id = "technical-specialist",
  description = "Diagnoses and resolves technical problems, bugs, and service outages"
)
public class TechnicalSpecialist extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal("Diagnose and resolve technical issues for customers.")
      .canAcceptTasks(SupportTasks.RESOLVE)
      .maxIterationsPerTask(5);
  }
}
