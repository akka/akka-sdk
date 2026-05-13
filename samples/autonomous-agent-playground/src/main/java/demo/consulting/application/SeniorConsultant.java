package demo.consulting.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "senior-consultant",
  description = """
    Senior consultant for complex problems involving regulatory, M&A, \
    or enterprise transformation\
    """
)
public class SeniorConsultant extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .tools(new ConsultingTools())
      .capability(TaskAcceptance.of(ConsultingTasks.ENGAGEMENT));
  }
}
