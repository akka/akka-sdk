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

  public record SupportResponse(String id) {}

  public record SupportStatus(String status, SupportTasks.SupportResolution result) {}

  private final ComponentClient componentClient;

  public SupportEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public SupportResponse create(SupportRequest request) {
    var taskId = componentClient
      .forAutonomousAgent(TriageAgent.class, UUID.randomUUID().toString())
      .runSingleTask(SupportTasks.RESOLVE.instructions(request.issue()));
    return new SupportResponse(taskId);
  }

  @Get("/{taskId}")
  public SupportStatus get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(SupportTasks.RESOLVE);
    return new SupportStatus(snapshot.status().name(), snapshot.result());
  }
}
