package demo.devteam.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.devteam.application.ProjectLead;
import demo.devteam.application.ProjectTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/devteam")
public class DevTeamEndpoint {

  public record ProjectRequest(String description) {}

  public record ProjectResponse(String taskId) {}

  private final ComponentClient componentClient;

  public DevTeamEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ProjectResponse create(ProjectRequest request) {
    var taskId = componentClient
      .forAutonomousAgent(ProjectLead.class, UUID.randomUUID().toString())
      .runSingleTask(ProjectTasks.PLAN.instructions(request.description()));
    return new ProjectResponse(taskId);
  }
}
