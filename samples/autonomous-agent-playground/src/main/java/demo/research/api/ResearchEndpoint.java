package demo.research.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.research.application.ResearchBrief;
import demo.research.application.ResearchCoordinator;
import demo.research.application.ResearchTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/research")
public class ResearchEndpoint {

  public record ResearchRequest(String topic) {}

  public record ResearchResponse(String id) {}

  public record ResearchStatus(String status, ResearchBrief result) {}

  private final ComponentClient componentClient;

  public ResearchEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ResearchResponse create(ResearchRequest request) {
    var taskId = componentClient
      .forAutonomousAgent(ResearchCoordinator.class, UUID.randomUUID().toString())
      .runSingleTask(ResearchTasks.BRIEF.instructions(request.topic()));
    return new ResearchResponse(taskId);
  }

  @Get("/{taskId}")
  public ResearchStatus get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(ResearchTasks.BRIEF);
    return new ResearchStatus(snapshot.status().name(), snapshot.result());
  }
}
