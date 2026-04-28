package demo.devteam.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import demo.devteam.application.ProjectLead;
import demo.devteam.application.ProjectTasks;
import demo.devteam.application.ProjectTasks.ProjectResult;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/devteam")
public class DevTeamEndpoint extends AbstractHttpEndpoint {

  public record ProjectRequest(String description) {}

  public record ProjectResponse(String taskId, String runId, String agentComponentId) {}

  private final ComponentClient componentClient;

  public DevTeamEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ProjectResponse create(ProjectRequest request) {
    var agentInstanceId = requestContext().queryParams().getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    var taskId = componentClient
      .forAutonomousAgent(ProjectLead.class, agentInstanceId)
      .runSingleTask(ProjectTasks.PLAN.instructions(request.description()));
    return new ProjectResponse(taskId, agentInstanceId, "project-lead");
  }

  @Get("/{taskId}")
  public ProjectResult get(String taskId) {
    return componentClient.forTask(taskId).get(ProjectTasks.PLAN).result();
  }
}
