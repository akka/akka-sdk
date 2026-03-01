package demo.devteam.application;

import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskTemplate;

public class DeveloperTasks {

  public static final TaskTemplate<CodeDeliverable> IMPLEMENT = Task.of(
    "Implement a feature",
    CodeDeliverable.class
  ).instructionTemplate("Implement: {feature}. Requirements: {requirements}.");
}
