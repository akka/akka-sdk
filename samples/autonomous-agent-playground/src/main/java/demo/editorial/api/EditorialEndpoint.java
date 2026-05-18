package demo.editorial.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import demo.editorial.application.EditorInChief;
import demo.editorial.application.EditorialTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/editorial")
public class EditorialEndpoint extends AbstractHttpEndpoint {

  public record TopicRequest(String topic) {}

  public record TopicResponse(String taskId, String runId, String agentComponentId) {}

  private final ComponentClient componentClient;

  public EditorialEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public TopicResponse create(TopicRequest request) {
    var agentInstanceId = requestContext()
      .queryParams()
      .getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    var taskId = componentClient
      .forAutonomousAgent(EditorInChief.class, agentInstanceId)
      .runSingleTask(EditorialTasks.ARTICLE.instructions(request.topic()));
    return new TopicResponse(taskId, agentInstanceId, "editor-in-chief");
  }

  @Get("/{taskId}")
  public HttpResponse get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(EditorialTasks.ARTICLE);
    return snapshot
      .result()
      .<HttpResponse>map(HttpResponses::ok)
      .orElseGet(() -> HttpResponses.accepted(snapshot.status().name()));
  }
}
