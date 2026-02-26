package demo.devteam.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.devteam.application.ProjectResult;
import demo.devteam.application.TeamLead;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/projects")
public class ProjectEndpoint {

  public record CreateProject(String description) {}

  public record ProjectResponse(String id) {}

  public record ProjectStatusResponse(
    String status,
    ProjectResult result,
    String rawResult
  ) {}

  private final ComponentClient componentClient;

  public ProjectEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ProjectResponse create(CreateProject request) {
    var taskId = componentClient
      .forAutonomousAgent(TeamLead.class)
      .runSingleTask(request.description(), ProjectResult.class);

    return new ProjectResponse(taskId);
  }

  @Get("/{id}")
  public ProjectStatusResponse get(String id) {
    var task = componentClient.forTask(id, ProjectResult.class);
    var state = task.getState();
    return new ProjectStatusResponse(state.status().name(), task.getResult(), state.result());
  }
}
