package demo.research.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import demo.research.application.ResearchBrief;
import demo.research.application.ResearchCoordinator;
import demo.research.application.ResearchTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/research")
public class ResearchEndpoint extends AbstractHttpEndpoint {

  public record ResearchRequest(String topic) {}

  public record ResearchResponse(String id, String runId, String agentComponentId) {}

  public record ResearchStatus(String status, ResearchBrief result) {}

  private final ComponentClient componentClient;

  public ResearchEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ResearchResponse create(ResearchRequest request) {
    var agentInstanceId = requestContext()
      .queryParams()
      .getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    var taskId = componentClient
      .forAutonomousAgent(ResearchCoordinator.class, agentInstanceId)
      .runSingleTask(ResearchTasks.BRIEF.instructions(request.topic()));
    return new ResearchResponse(taskId, agentInstanceId, "research-coordinator");
  }

  @Get("/{taskId}")
  public HttpResponse get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(ResearchTasks.BRIEF);
    return snapshot
      .result()
      .<HttpResponse>map(
        result -> HttpResponses.ok(new ResearchStatus(snapshot.status().name(), result))
      )
      .orElseGet(() -> HttpResponses.accepted(snapshot.status().name()));
  }
}
