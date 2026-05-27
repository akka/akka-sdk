package demo.multiagent.api;

// tag::all[]
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import demo.multiagent.application.ActivityCoordinator;
import demo.multiagent.application.ActivityTasks;
import demo.multiagent.application.PreferencesEntity;
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
    var instructions = "User: " + userId + "\n\n" + request.message(); // <1>

    var taskId = componentClient
      .forAutonomousAgent(ActivityCoordinator.class, UUID.randomUUID().toString())
      .runSingleTask(ActivityTasks.SUGGEST_ACTIVITIES.instructions(instructions)); // <2>

    return HttpResponses.created(taskId, "/activities/" + userId + "/" + taskId);
  }

  @Get("/activities/{userId}/{taskId}")
  public HttpResponse getAnswer(String userId, String taskId) {
    var snapshot = componentClient.forTask(taskId).get(ActivityTasks.SUGGEST_ACTIVITIES); // <3>

    return snapshot
      .result()
      .<HttpResponse>map(HttpResponses::ok)
      .orElseGet(
        () -> HttpResponses.notFound("Answer for '" + taskId + "' not available (yet)")
      );
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
// end::all[]
