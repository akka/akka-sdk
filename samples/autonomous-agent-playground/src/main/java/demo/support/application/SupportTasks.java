package demo.support.application;

import akka.javasdk.agent.task.Task;

public class SupportTasks {

  public static final Task<SupportResolution> RESOLVE = Task.of(
    "Resolve a support ticket",
    SupportResolution.class
  );
}
