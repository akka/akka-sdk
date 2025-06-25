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
  record Rows(List<Row> entries) {}

  record Row(String userId, String userQuestion, String answer) {}

  @Query("SELECT * FROM activities WHERE userId = :userId") // <1>
  public QueryEffect<Rows> getActivities(String userId) {
    return queryResult();
  }

  @Consume.FromWorkflow(AgentTeam.class) // <2>
  public static class Updater extends TableUpdater<Row> {
    public Effect<Row> onStateChange(AgentTeam.State state) {
      return effects()
          .updateRow(new Row(state.userId(), state.userQuery(), state.answer()));
    }

    @DeleteHandler
    public Effect<Row> onDelete() {
      return effects().deleteRow();
    }
  }

}
// end::class[]
