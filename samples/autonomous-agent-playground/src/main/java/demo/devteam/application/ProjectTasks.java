package demo.devteam.application;

import akka.javasdk.agent.task.Task;

public class ProjectTasks {

  public static final Task<ProjectResult> PLAN = Task.of(
    "Plan and execute a project",
    ProjectResult.class
  );
}
