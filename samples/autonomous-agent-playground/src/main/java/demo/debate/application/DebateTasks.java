package demo.debate.application;

import akka.javasdk.agent.task.Task;
import java.util.List;

public class DebateTasks {

  public record DebateResult(String topic, String synthesis, List<String> keyArguments) {}

  // prettier-ignore
  public static final Task<DebateResult> DEBATE = Task
    .define("Debate")
    .description("Moderate a structured debate between advocates and critics, then synthesize a balanced conclusion.")
    .resultConformsTo(DebateResult.class);
}
