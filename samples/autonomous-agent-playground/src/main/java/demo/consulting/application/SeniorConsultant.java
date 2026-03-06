package demo.consulting.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(
  id = "senior-consultant",
  description = "Senior consultant for complex problems involving regulatory, M&A, " +
  "or enterprise transformation"
)
public class SeniorConsultant extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal(
        """
        Resolve complex, high-stakes consulting problems — regulatory issues, \
        M&A integration, enterprise transformation. Deliver a comprehensive \
        recommendation with actionable next steps. \
        """
      )
      .accepts(ConsultingTasks.ENGAGEMENT)
      .tools(new ConsultingTools())
      .maxIterations(10);
  }
}
