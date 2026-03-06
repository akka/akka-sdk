package demo.consulting.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(
  id = "consulting-researcher",
  description = "Researches a specific aspect of a client problem"
)
public class ConsultingResearcher extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
      .goal(
        """
        Research the given topic thoroughly and produce a clear, \
        factual summary of your findings. \
        """
      )
      .accepts(ConsultingTasks.RESEARCH)
      .tools(new ConsultingTools())
      .maxIterations(5);
  }
}
