package demo.consulting.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(id = "consulting-researcher",
    description = "Performs targeted research on specific aspects of consulting problems")
public class ConsultingResearcher extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
        .goal("Perform targeted research on consulting problems. " +
            "Investigate the specific topic thoroughly and produce clear, actionable findings.")
        .accepts(ConsultingTasks.RESEARCH)
        .maxIterations(3);
  }
}
