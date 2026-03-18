package demo.debate.application;

import akka.javasdk.agent.task.Task;

public class DebateTasks {

  public static final Task<DebateResult> DEBATE = Task.of(
    "Moderate a debate",
    DebateResult.class
  );
}
