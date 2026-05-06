package demo.devteam.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import demo.devteam.application.ProjectLead;
import demo.devteam.application.ProjectTasks;
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
    var agentInstanceId = requestContext()
      .queryParams()
      .getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    var taskId = componentClient
      .forAutonomousAgent(ProjectLead.class, agentInstanceId)
      .runSingleTask(ProjectTasks.PLAN.instructions(request.description()));
    return new ProjectResponse(taskId, agentInstanceId, "project-lead");
  }

  @Get("/{taskId}")
  public HttpResponse get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(ProjectTasks.PLAN);
    return snapshot
      .result()
      .<HttpResponse>map(HttpResponses::ok)
      .orElseGet(() -> HttpResponses.accepted(snapshot.status().name()));
  }
}
