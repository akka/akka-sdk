package demo.consulting.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import demo.consulting.application.ConsultingCoordinator;
import demo.consulting.application.ConsultingTasks;
import demo.consulting.domain.ConsultingResult;

import java.util.UUID;

@HttpEndpoint("/engagements")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ConsultingEndpoint {

  public record EngagementRequest(String problem) {}
  public record EngagementResponse(String taskId) {}
  public record EngagementStatus(String status, ConsultingResult result) {}

  private final ComponentClient componentClient;

  public ConsultingEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public EngagementResponse submitEngagement(EngagementRequest request) {
    if (request.problem() == null || request.problem().isBlank()) {
      throw HttpException.badRequest("Problem description must not be empty");
    }

    var taskId = componentClient
        .forAutonomousAgent(ConsultingCoordinator.class, UUID.randomUUID().toString())
        .runSingleTask(ConsultingTasks.ENGAGEMENT.instructions(request.problem()));

    return new EngagementResponse(taskId);
  }

  @Get("/{taskId}")
  public EngagementStatus getResult(String taskId) {
    var snapshot = componentClient.forTask(ConsultingTasks.ENGAGEMENT).get(taskId);
    return new EngagementStatus(snapshot.status().name(), snapshot.result());
  }
}
