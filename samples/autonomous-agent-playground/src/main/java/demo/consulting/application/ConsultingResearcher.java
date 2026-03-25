package demo.consulting.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "consulting-researcher",
  description = "Researches a specific aspect of a client problem"
)
public class ConsultingResearcher extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        """
        Research the given topic thoroughly and produce a clear, \
        factual summary of your findings. \
        """
      )
      .tools(new ConsultingTools())
      .capability(TaskAcceptance.of(ConsultingTasks.RESEARCH).maxIterationsPerTask(5));
  }
}
