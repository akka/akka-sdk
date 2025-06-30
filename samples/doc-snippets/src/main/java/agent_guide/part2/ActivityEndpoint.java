package agent_guide.part2;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;

import java.util.UUID;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint()
public class ActivityEndpoint {
  public record Request(String message) {
  }

  // tag::addPreference[]
  public record AddPreference(String preference) {
  }

  // end::addPreference[]

  private final ComponentClient componentClient;

  public ActivityEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::userId[]
  @Post("/activities/{userId}")
  public String suggestActivities(String userId, Request request) { // <1>
    var sessionId = UUID.randomUUID().toString();
    return componentClient
        .forAgent()
        .inSession(sessionId)
        .method(ActivityAgent::query)
        .invoke(new ActivityAgent.Request(userId, request.message())); // <2>
  }
  // end::userId[]

  // tag::addPreference[]
  @Post("/preferences/{userId}")
  public HttpResponse addPreference(String userId, AddPreference request) { // <1>
    componentClient
        .forEventSourcedEntity(userId)
        .method(PreferencesEntity::addPreference)
        .invoke(new PreferencesEntity.AddPreference(request.preference())); // <2>

    return HttpResponses.created();
  }
  // end::addPreference[]
}
