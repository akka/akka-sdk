package demo.activities.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import demo.activities.application.ActivityWorkflow;
import demo.activities.application.PreferencesEntity;
import java.util.UUID;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class ActivityEndpoint {

  public record Request(String message) {}

  public record AddPreference(String preference) {}

  private final ComponentClient componentClient;

  public ActivityEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/activities/{userId}")
  public HttpResponse suggestActivities(String userId, Request request) {
    var workflowId = UUID.randomUUID().toString();
    componentClient
      .forWorkflow(workflowId)
      .method(ActivityWorkflow::start)
      .invoke(new ActivityWorkflow.Request(userId, request.message()));

    return HttpResponses.created(workflowId, "/activities/" + userId + "/" + workflowId);
  }

  @Get("/activities/{userId}/{workflowId}")
  public ActivityWorkflow.Result getResult(String userId, String workflowId) {
    return componentClient
      .forWorkflow(workflowId)
      .method(ActivityWorkflow::getResult)
      .invoke();
  }

  @Post("/preferences/{userId}")
  public HttpResponse addPreference(String userId, AddPreference request) {
    componentClient
      .forEventSourcedEntity(userId)
      .method(PreferencesEntity::addPreference)
      .invoke(new PreferencesEntity.AddPreference(request.preference()));

    return HttpResponses.created();
  }
}
