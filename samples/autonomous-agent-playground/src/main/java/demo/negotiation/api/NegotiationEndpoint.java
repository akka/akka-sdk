package demo.negotiation.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.negotiation.application.Facilitator;
import demo.negotiation.application.NegotiationTasks;
import demo.negotiation.application.NegotiationTasks.NegotiationResult;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/negotiation")
public class NegotiationEndpoint {

  public record NegotiationRequest(String topic) {}

  public record NegotiationResponse(String taskId) {}

  private final ComponentClient componentClient;

  public NegotiationEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public NegotiationResponse create(NegotiationRequest request) {
    var taskId = componentClient
      .forAutonomousAgent(Facilitator.class, UUID.randomUUID().toString())
      .runSingleTask(NegotiationTasks.NEGOTIATE.instructions(request.topic()));
    return new NegotiationResponse(taskId);
  }

  @Get("/{taskId}")
  public NegotiationResult get(String taskId) {
    return componentClient.forTask(taskId).get(NegotiationTasks.NEGOTIATE).result();
  }
}
