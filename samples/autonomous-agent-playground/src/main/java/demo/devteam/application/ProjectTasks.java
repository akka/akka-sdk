package demo.devteam.application;

import akka.javasdk.agent.task.Task;
import java.util.List;

public class ProjectTasks {

  public record ProjectResult(String summary, List<String> deliverables) {}

  public static final Task<ProjectResult> PLAN =
      Task.define("Plan and execute a project")
          .description("Break work into tasks, coordinate a team, and deliver results")
          .resultConformsTo(ProjectResult.class);
}
