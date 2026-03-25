package demo.support.application;

import akka.javasdk.agent.task.Task;

public class SupportTasks {

  public record SupportResolution(String category, String resolution, boolean resolved) {}

  // prettier-ignore
  public static final Task<SupportResolution> RESOLVE = Task
    .define("Resolve")
    .description("Resolve a customer support request")
    .resultConformsTo(SupportResolution.class);
}
