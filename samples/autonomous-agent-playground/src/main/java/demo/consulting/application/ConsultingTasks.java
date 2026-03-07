package demo.consulting.application;

import akka.javasdk.agent.task.Task;

public class ConsultingTasks {

  public static final Task<ConsultingResult> ENGAGEMENT = Task.of(
    "Consulting engagement",
    ConsultingResult.class
  );
}
