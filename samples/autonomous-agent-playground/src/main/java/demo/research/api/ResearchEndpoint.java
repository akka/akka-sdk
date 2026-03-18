package demo.research.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.research.application.BriefCoordinator;
import demo.research.application.ResearchBrief;
import demo.research.application.ResearchTasks;
import java.util.Map;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/research")
public class ResearchEndpoint {

  public record CreateBrief(String topic, String focus) {}

  public record BriefResponse(String id) {}

  public record BriefStatusResponse(String status, ResearchBrief brief) {}

  private final ComponentClient componentClient;

  public ResearchEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public BriefResponse create(CreateBrief request) {
    var ref = componentClient
      .forAutonomousAgent(BriefCoordinator.class)
      .runSingleTask(
        ResearchTasks.BRIEF.params(Map.of("topic", request.topic(), "focus", request.focus()))
      );

    return new BriefResponse(ref.taskId());
  }

  @Get("/{id}")
  public BriefStatusResponse get(String id) {
    var task = componentClient.forTask(ResearchTasks.BRIEF.ref(id)).get();
    return new BriefStatusResponse(task.status().name(), task.result());
  }
}
