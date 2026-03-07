package demo.devteam.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.devteam.application.ProjectResult;
import demo.devteam.application.ProjectTasks;
import demo.devteam.application.TeamLead;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/projects")
public class ProjectEndpoint {

  public record CreateProject(String description) {}

  public record ProjectResponse(String id) {}

  public record ProjectStatusResponse(String status, ProjectResult result) {}

  private final ComponentClient componentClient;

  public ProjectEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ProjectResponse create(CreateProject request) {
    var ref = componentClient
      .forAutonomousAgent(TeamLead.class)
      .runSingleTask(ProjectTasks.PLAN.instructions(request.description()));

    return new ProjectResponse(ref.taskId());
  }

  @Get("/{id}")
  public ProjectStatusResponse get(String id) {
    var task = componentClient.forTask(ProjectTasks.PLAN.ref(id)).get();
    return new ProjectStatusResponse(task.status().name(), task.result());
  }
}
