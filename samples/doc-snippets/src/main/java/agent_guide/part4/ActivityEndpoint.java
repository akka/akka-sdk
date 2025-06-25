package agent_guide.part4;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;

import java.util.UUID;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint()
public class ActivityEndpoint {
  public record Request(String message) {
  }

  private final ComponentClient componentClient;

  public ActivityEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::workflow[]
  @Post("/activities/{userId}")
  public HttpResponse suggestActivities(String userId, Request request) {
    var sessionId = UUID.randomUUID().toString();

    var res =
      componentClient
      .forWorkflow(sessionId)
        .method(AgentTeamWorkflow::start) // <1>
        .invoke(new AgentTeamWorkflow.Request(userId, request.message()));

    return HttpResponses.created(res, "/activities/ " + userId + "/" + sessionId); // <2>
  }
  // end::workflow[]

  // tag::get[]
  @Get("/activities/{userId}/{sessionId}")
  public HttpResponse getAnswer(String userId, String sessionId) {
    var res =
        componentClient
            .forWorkflow(sessionId)
            .method(AgentTeamWorkflow::getAnswer)
            .invoke();

    if (res.isEmpty())
      return HttpResponses.notFound("Answer for '" + sessionId + "' not available (yet)");
    else
      return HttpResponses.ok(res);
  }
  // end::get[]

}
