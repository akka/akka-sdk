package agent_guide.part5;

// tag::class[]
import agent_guide.part4.AgentTeam;
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

  public record ActivityEntry(String userId, String userQuestion, String finalAnswer) {}

  @Query("SELECT * FROM activities WHERE userId = :userId") // <1>
  public QueryEffect<ActivityEntries> getActivities(String userId) {
    return queryResult();
  }

  @Consume.FromWorkflow(AgentTeam.class) // <2>
  public static class Updater extends TableUpdater<ActivityEntry> {
    public Effect<ActivityEntry> onStateChange(AgentTeam.State state) {
      return effects()
          .updateRow(new ActivityEntry(state.userId(), state.userQuery(), state.finalAnswer()));
    }

    @DeleteHandler
    public Effect<ActivityEntry> onDelete() {
      return effects().deleteRow();
    }
  }

}
// end::class[]
