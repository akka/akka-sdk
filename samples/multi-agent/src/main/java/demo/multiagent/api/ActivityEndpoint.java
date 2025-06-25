package demo.multiagent.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import demo.multiagent.application.AgentTeam;

import java.util.UUID;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("")
public class ActivityEndpoint {

  public record Request(String message) {
  }

  private final ComponentClient componentClient;

  public ActivityEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/activities")
  public HttpResponse suggestActivities( Request request) {
    var sessionId = UUID.randomUUID().toString();

    var res =
      componentClient
      .forWorkflow(sessionId)
        .method(AgentTeam::start)
        .invoke(request.message);

    return HttpResponses.created(res, "/activities/" + sessionId);
  }

  @Get("/activities/{sessionId}")
  public HttpResponse getAnswer(String sessionId) {
      var res =
        componentClient
          .forWorkflow(sessionId)
          .method(AgentTeam::getAnswer)
          .invoke();

      if (res.isEmpty())
        return HttpResponses.notFound("Answer for '" + sessionId + "' not available (yet)");
      else
        return HttpResponses.ok(res);
  }
}
