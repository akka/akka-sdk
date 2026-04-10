package demo.debate.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.debate.application.DebateModerator;
import demo.debate.application.DebateTasks;
import demo.debate.application.DebateTasks.DebateResult;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/debate")
public class DebateEndpoint {

  public record DebateRequest(String topic) {}

  public record DebateResponse(String taskId) {}

  private final ComponentClient componentClient;

  public DebateEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public DebateResponse create(DebateRequest request) {
    var taskId = componentClient
      .forAutonomousAgent(DebateModerator.class, UUID.randomUUID().toString())
      .runSingleTask(DebateTasks.DEBATE.instructions(request.topic()));
    return new DebateResponse(taskId);
  }

  @Get("/{taskId}")
  public DebateResult get(String taskId) {
    return componentClient.forTask(taskId).get(DebateTasks.DEBATE).result();
  }
}
