package demo.multiagent.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import demo.multiagent.application.ActivityView;
import demo.multiagent.application.AgentTeamWorkflow;
import demo.multiagent.application.PreferencesEntity;

import java.util.List;
import java.util.UUID;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint()
public class ActivityEndpoint {

  public record Request(String message) {
  }

  public record AddPreference(String preference) {
  }

  public record ActivitiesList(List<Suggestion> suggestions) {
    static ActivitiesList fromView(ActivityView.ActivityEntries entries) {
      return new ActivitiesList(entries.entries().stream().map(Suggestion::fromView).toList());
    }
  }

  public record Suggestion(String userQuestion, String answer) {
    static Suggestion fromView(ActivityView.ActivityEntry entry) {
      return new Suggestion(entry.userQuestion(), entry.finalAnswer());
    }
  }

  private final ComponentClient componentClient;

  public ActivityEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/activities/{userId}")
  public HttpResponse suggestActivities(String userId, Request request) {
    var sessionId = UUID.randomUUID().toString();

    var res =
      componentClient
      .forWorkflow(sessionId)
        .method(AgentTeamWorkflow::start)
        .invoke(new AgentTeamWorkflow.Request(userId, request.message()));

    return HttpResponses.created(res, "/activities/" + userId + "/" + sessionId);
  }

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

  @Get("/activities/{userId}")
  public ActivitiesList listActivities(String userId) {
    var viewResult =  componentClient
        .forView()
        .method(ActivityView::getActivities)
        .invoke(userId);

    return ActivitiesList.fromView(viewResult);
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
