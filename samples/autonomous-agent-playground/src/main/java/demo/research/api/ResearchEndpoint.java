package demo.research.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.research.application.BriefCoordinator;
import demo.research.application.ResearchBrief;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/research")
public class ResearchEndpoint {

  public record CreateBrief(String topic) {}

  public record BriefResponse(String id) {}

  public record BriefStatusResponse(String status, ResearchBrief brief, String result) {}

  private final ComponentClient componentClient;

  public ResearchEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public BriefResponse create(CreateBrief request) {
    var taskId = componentClient
      .forAutonomousAgent(BriefCoordinator.class)
      .runSingleTask(request.topic(), ResearchBrief.class);

    return new BriefResponse(taskId);
  }

  @Get("/{id}")
  public BriefStatusResponse get(String id) {
    var task = componentClient.forTask(id, ResearchBrief.class);
    var state = task.getState();
    return new BriefStatusResponse(state.status().name(), task.getResult(), state.result());
  }
}
