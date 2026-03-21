package demo.devteam.application;

import akka.javasdk.agent.task.Task;
import java.util.List;

public class ProjectTasks {

  public record ProjectResult(String summary, List<String> deliverables) {}

  // prettier-ignore
  public static final Task<ProjectResult> PLAN = Task
    .define("Plan")
    .description("Plan project: break work into tasks, coordinate a team, and deliver results.")
    .resultConformsTo(ProjectResult.class);
}
