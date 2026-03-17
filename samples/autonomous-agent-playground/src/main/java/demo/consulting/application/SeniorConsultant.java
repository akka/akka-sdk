package demo.consulting.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.annotations.Component;

@Component(
  id = "senior-consultant",
  description = "Senior consultant for complex problems involving regulatory, M&A, " +
  "or enterprise transformation"
)
public class SeniorConsultant extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        """
        Resolve complex, high-stakes consulting problems — regulatory issues, \
        M&A integration, enterprise transformation. Deliver a comprehensive \
        recommendation with actionable next steps. \
        """
      )
      .tools(new ConsultingTools())
      .capabilities(
          canAcceptTasks(ConsultingTasks.ENGAGEMENT));
  }
}
