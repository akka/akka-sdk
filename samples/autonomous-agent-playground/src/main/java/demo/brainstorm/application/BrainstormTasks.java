package demo.brainstorm.application;

import akka.javasdk.agent.task.Task;

public class BrainstormTasks {

  public static final Task<BrainstormResult> IDEATE = Task.of(
    "Brainstorm ideas",
    BrainstormResult.class
  );

  public static final Task<BrainstormResult> CURATE = Task.of(
    "Curate brainstorm results",
    BrainstormResult.class
  );
}
