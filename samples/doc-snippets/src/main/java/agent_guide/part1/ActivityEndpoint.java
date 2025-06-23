package agent_guide.part1;

// tag::class[]
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;

import java.util.UUID;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("")
public class ActivityEndpoint {
  public record Request(String message) {
  }

  private final ComponentClient componentClient;

  public ActivityEndpoint(ComponentClient componentClient) { // <1>
    this.componentClient = componentClient;
  }

  @Post("/activities")
  public String suggestActivities(Request request) {
    var sessionId = UUID.randomUUID().toString();
    return componentClient
        .forAgent()
        .inSession(sessionId)
        .method(ActivityAgent::query) // <2>
        .invoke(request.message());
  }
}
// end::class[]
