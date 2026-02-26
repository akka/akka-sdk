package demo.support.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.support.application.SupportResolution;
import demo.support.application.TriageAgent;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/support")
public class SupportEndpoint {

  public record CreateTicket(String issue) {}

  public record TicketResponse(String id) {}

  public record TicketStatusResponse(
    String status,
    SupportResolution resolution,
    String result
  ) {}

  private final ComponentClient componentClient;

  public SupportEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public TicketResponse create(CreateTicket request) {
    var taskId = componentClient
      .forAutonomousAgent(TriageAgent.class)
      .runSingleTask(request.issue(), SupportResolution.class);

    return new TicketResponse(taskId);
  }

  @Get("/{id}")
  public TicketStatusResponse get(String id) {
    var task = componentClient.forTask(id, SupportResolution.class);
    var state = task.getState();
    return new TicketStatusResponse(state.status().name(), task.getResult(), state.result());
  }
}
