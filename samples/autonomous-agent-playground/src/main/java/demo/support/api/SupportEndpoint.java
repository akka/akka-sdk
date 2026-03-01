package demo.support.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.support.application.SupportResolution;
import demo.support.application.SupportTasks;
import demo.support.application.TriageAgent;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/support")
public class SupportEndpoint {

  public record CreateTicket(String issue) {}

  public record TicketResponse(String id) {}

  public record TicketStatusResponse(String status, SupportResolution resolution) {}

  private final ComponentClient componentClient;

  public SupportEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public TicketResponse create(CreateTicket request) {
    var ref = componentClient
      .forAutonomousAgent(TriageAgent.class)
      .runSingleTask(SupportTasks.RESOLVE.instructions(request.issue()));

    return new TicketResponse(ref.taskId());
  }

  @Get("/{id}")
  public TicketStatusResponse get(String id) {
    var task = componentClient.forTask(SupportTasks.RESOLVE.ref(id)).get();
    return new TicketStatusResponse(task.status().name(), task.result());
  }
}
