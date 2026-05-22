package demo.debate.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import demo.debate.application.DebateModerator;
import demo.debate.application.DebateTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/debate")
public class DebateEndpoint extends AbstractHttpEndpoint {

  public record DebateRequest(String topic) {}

  public record DebateResponse(String taskId, String runId, String agentComponentId) {}

  private final ComponentClient componentClient;

  public DebateEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public DebateResponse create(DebateRequest request) {
    var agentInstanceId = requestContext()
      .queryParams()
      .getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    var taskId = componentClient
      .forAutonomousAgent(DebateModerator.class, agentInstanceId)
      .runSingleTask(DebateTasks.DEBATE.instructions(request.topic()));
    return new DebateResponse(taskId, agentInstanceId, "debate-moderator");
  }

  @Get("/{taskId}")
  public HttpResponse get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(DebateTasks.DEBATE);
    return snapshot
      .result()
      .<HttpResponse>map(HttpResponses::ok)
      .orElseGet(() -> HttpResponses.accepted(snapshot.status().name()));
  }
}
