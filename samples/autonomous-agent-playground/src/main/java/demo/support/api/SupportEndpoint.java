package demo.support.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.support.application.SupportTasks;
import demo.support.application.TriageAgent;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/support")
public class SupportEndpoint {

  public record SupportRequest(String issue) {}

  public record SupportResponse(String id, String runId, String agentComponentId) {}

  public record SupportStatus(String status, SupportTasks.SupportResolution result) {}

  private final ComponentClient componentClient;

  public SupportEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public SupportResponse create(SupportRequest request) {
    var agentInstanceId = UUID.randomUUID().toString();
    var taskId = componentClient
      .forAutonomousAgent(TriageAgent.class, agentInstanceId)
      .runSingleTask(SupportTasks.RESOLVE.instructions(request.issue()));
    return new SupportResponse(taskId, agentInstanceId, "triage-agent");
  }

  @Get("/{taskId}")
  public SupportStatus get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(SupportTasks.RESOLVE);
    return new SupportStatus(snapshot.status().name(), snapshot.result());
  }
}
