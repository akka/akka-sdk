package demo.support.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import demo.support.application.SupportTasks;
import demo.support.application.TriageAgent;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/support")
public class SupportEndpoint extends AbstractHttpEndpoint {

  public record SupportRequest(String issue) {}

  public record SupportResponse(String id, String runId, String agentComponentId) {}

  public record SupportStatus(String status, SupportTasks.SupportResolution result) {}

  private final ComponentClient componentClient;

  public SupportEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public SupportResponse create(SupportRequest request) {
    var agentInstanceId = requestContext()
      .queryParams()
      .getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    var taskId = componentClient
      .forAutonomousAgent(TriageAgent.class, agentInstanceId)
      .runSingleTask(SupportTasks.RESOLVE.instructions(request.issue()));
    return new SupportResponse(taskId, agentInstanceId, "triage-agent");
  }

  @Get("/{taskId}")
  public HttpResponse get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(SupportTasks.RESOLVE);
    return snapshot
      .result()
      .<HttpResponse>map(
        result -> HttpResponses.ok(new SupportStatus(snapshot.status().name(), result))
      )
      .orElseGet(() -> HttpResponses.accepted(snapshot.status().name()));
  }
}
