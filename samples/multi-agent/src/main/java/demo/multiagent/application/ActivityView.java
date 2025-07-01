package demo.multiagent.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

import java.util.List;

@ComponentId("activity-view")
public class ActivityView extends View {
  public record ActivityEntries(List<ActivityEntry> entries) {}

  public record ActivityEntry(String userId, String sessionId, String userQuestion, String finalAnswer) {}

  @Query("SELECT * as entries FROM activities WHERE userId = :userId")
  public QueryEffect<ActivityEntries> getActivities(String userId) {
    return queryResult();
  }

  @Consume.FromWorkflow(AgentTeamWorkflow.class)
  public static class Updater extends TableUpdater<ActivityEntry> {
    public Effect<ActivityEntry> onStateChange(AgentTeamWorkflow.State state) {
      var sessionId = updateContext().eventSubject().get(); // the workflow id
      return effects()
          .updateRow(new ActivityEntry(
              state.userId(),
              sessionId,
              state.userQuery(),
              state.finalAnswer()));
    }

    @DeleteHandler
    public Effect<ActivityEntry> onDelete() {
      return effects().deleteRow();
    }
  }

}
