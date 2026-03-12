package demo.consulting.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(id = "senior-consultant",
    description = "Handles complex, high-stakes consulting issues requiring senior expertise")
public class SeniorConsultant extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
        .goal("Resolve complex, high-stakes consulting issues. " +
            "Apply senior expertise to regulatory, M&A, and compliance problems. " +
            "Provide thorough assessment and strategic recommendations.")
        .accepts(ConsultingTasks.ENGAGEMENT)
        .tools(new ConsultingTools())
        .maxIterations(5);
  }
}
