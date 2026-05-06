package demo.consulting.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import demo.consulting.application.ConsultingCoordinator;
import demo.consulting.application.ConsultingTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/consulting")
public class ConsultingEndpoint extends AbstractHttpEndpoint {

  public record ConsultingRequest(String problem) {}

  public record ConsultingResponse(String id, String runId, String agentComponentId) {}

  public record ConsultingStatus(String status, ConsultingTasks.ConsultingResult result) {}

  private final ComponentClient componentClient;

  public ConsultingEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ConsultingResponse create(ConsultingRequest request) {
    var agentInstanceId = requestContext()
      .queryParams()
      .getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    var taskId = componentClient
      .forAutonomousAgent(ConsultingCoordinator.class, agentInstanceId)
      .runSingleTask(ConsultingTasks.ENGAGEMENT.instructions(request.problem()));
    return new ConsultingResponse(taskId, agentInstanceId, "consulting-coordinator");
  }

  @Get("/{taskId}")
  public ConsultingStatus get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(ConsultingTasks.ENGAGEMENT);
    return new ConsultingStatus(snapshot.status().name(), snapshot.result());
  }
}
