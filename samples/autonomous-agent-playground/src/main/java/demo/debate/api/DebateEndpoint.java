package demo.debate.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.debate.application.DebateModerator;
import demo.debate.application.DebateTasks;
import demo.debate.application.DebateResult;

/**
 * Structured debate with messaging-driven collaboration.
 *
 * <p>Usage:
 *
 * <pre>
 * # Start a debate
 * curl -X POST localhost:9000/debate -H "Content-Type: application/json" \
 *   -d '{"topic": "Should AI systems be required to explain their decisions?"}'
 *
 * # Check status
 * curl localhost:9000/debate/{id}
 * </pre>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/debate")
public class DebateEndpoint {

  public record CreateDebate(String topic) {}

  public record DebateResponse(String id) {}

  public record DebateStatusResponse(String status, DebateResult result) {}

  private final ComponentClient componentClient;

  public DebateEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public DebateResponse create(CreateDebate request) {
    var ref = componentClient
      .forAutonomousAgent(DebateModerator.class)
      .runSingleTask(
        DebateTasks.DEBATE.instructions("Moderate a debate on: " + request.topic())
      );

    return new DebateResponse(ref.taskId());
  }

  @Get("/{id}")
  public DebateStatusResponse get(String id) {
    var task = componentClient.forTask(DebateTasks.DEBATE.ref(id)).get();
    return new DebateStatusResponse(task.status().name(), task.result());
  }
}
