package demo.devteam.application;

import akka.javasdk.agent.task.TaskTemplate;

public class DeveloperTasks {

  // prettier-ignore
  public static final TaskTemplate<CodeDeliverable> IMPLEMENT = TaskTemplate
    .define("Implement")
    .description("Implement a feature with clean, tested code")
    .resultConformsTo(CodeDeliverable.class)
    .instructionTemplate("Implement: {feature}. Requirements: {requirements}.");
}
