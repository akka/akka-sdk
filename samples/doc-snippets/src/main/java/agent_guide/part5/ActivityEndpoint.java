package agent_guide.part5;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;

import java.util.List;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint()
public class ActivityEndpoint {
  // tag::list[]
  public record ActivitiesList(List<Suggestion> suggestions) {
    static ActivitiesList fromView(ActivityView.ActivityEntries entries) {
      return new ActivitiesList(rows.entries().stream().map(Suggestion::fromView).toList());
    }
  }

  public record Suggestion(String userQuestion, String answer) {
    static Suggestion fromView(ActivityView.ActivityEntry entry) {
      return new Suggestion(entry.userQuestion(), entry.finalAnswer());
    }
  }

  // end::list[]

  private final ComponentClient componentClient;

  public ActivityEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::list[]
  @Get("/activities/{userId}")
  public ActivitiesList listActivities(String userId) {
      var viewResult =  componentClient
            .forView()
            .method(ActivityView::getActivities)
            .invoke(userId);

    return ActivitiesList.fromView(viewResult);
  }
  // end::list[]

}
