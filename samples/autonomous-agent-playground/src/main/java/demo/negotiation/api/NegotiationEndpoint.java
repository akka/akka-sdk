package demo.negotiation.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import demo.negotiation.application.Facilitator;
import demo.negotiation.application.NegotiationTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/negotiation")
public class NegotiationEndpoint extends AbstractHttpEndpoint {

  public record NegotiationRequest(String topic) {}

  public record NegotiationResponse(String taskId, String runId, String agentComponentId) {}

  private final ComponentClient componentClient;

  public NegotiationEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public NegotiationResponse create(NegotiationRequest request) {
    var agentInstanceId = requestContext()
      .queryParams()
      .getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    var taskId = componentClient
      .forAutonomousAgent(Facilitator.class, agentInstanceId)
      .runSingleTask(NegotiationTasks.NEGOTIATE.instructions(request.topic()));
    return new NegotiationResponse(taskId, agentInstanceId, "facilitator");
  }

  @Get("/{taskId}")
  public HttpResponse get(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(NegotiationTasks.NEGOTIATE);
    return snapshot
      .result()
      .<HttpResponse>map(HttpResponses::ok)
      .orElseGet(() -> HttpResponses.accepted(snapshot.status().name()));
  }
}
