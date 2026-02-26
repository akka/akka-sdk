package demo.consulting.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.consulting.application.ConsultingCoordinator;
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

  public record ConsultingStatusResponse(
    String status,
    ConsultingResult result,
    String rawResult
  ) {}

  private final ComponentClient componentClient;

  public ConsultingEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public ConsultingResponse create(CreateConsulting request) {
    var taskId = componentClient
      .forAutonomousAgent(ConsultingCoordinator.class)
      .runSingleTask("Consulting engagement: " + request.problem(), ConsultingResult.class);

    return new ConsultingResponse(taskId);
  }

  @Get("/{id}")
  public ConsultingStatusResponse get(String id) {
    var task = componentClient.forTask(id, ConsultingResult.class);
    var state = task.getState();
    return new ConsultingStatusResponse(
      state.status().name(),
      task.getResult(),
      state.result()
    );
  }
}
