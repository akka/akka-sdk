package demo.consulting.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.consulting.application.ConsultingCoordinator;
import demo.consulting.application.ConsultingTasks;
import demo.consulting.application.ConsultingResult;

/**
 * Consulting engagement with delegation + handoff.
 *
 * <p>Usage:
 *
 * <pre>
 * # Start a consulting engagement (standard problem)
 * curl -X POST localhost:9000/consulting -H "Content-Type: application/json" \
 *   -d '{"problem": "How to improve our supply chain efficiency"}'
 *
 * # Start a consulting engagement (complex problem — triggers escalation)
 * curl -X POST localhost:9000/consulting -H "Content-Type: application/json" \
 *   -d '{"problem": "Regulatory compliance for our upcoming merger"}'
 *
 * # Check status
 * curl localhost:9000/consulting/{id}
 * </pre>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/consulting")
public class ConsultingEndpoint {

  public record CreateConsulting(String problem) {}

  public record ConsultingResponse(String id) {}

  public record ConsultingStatusResponse(String status, ConsultingResult result) {}

  private final ComponentClient componentClient;

  public ConsultingEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ConsultingResponse create(CreateConsulting request) {
    var ref = componentClient
      .forAutonomousAgent(ConsultingCoordinator.class)
      .runSingleTask(
        ConsultingTasks.ENGAGEMENT.instructions("Consulting engagement: " + request.problem())
      );

    return new ConsultingResponse(ref.taskId());
  }

  @Get("/{id}")
  public ConsultingStatusResponse get(String id) {
    var task = componentClient.forTask(ConsultingTasks.ENGAGEMENT.ref(id)).get();
    return new ConsultingStatusResponse(task.status().name(), task.result());
  }
}
