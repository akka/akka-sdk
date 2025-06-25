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
  public record Rows(List<Row> entries) {}

  public record Row(String userId, String userQuestion, String finalAnswer) {}

  @Query("SELECT * as entries FROM activities WHERE userId = :userId")
  public QueryEffect<Rows> getActivities(String userId) {
    return queryResult();
  }

  @Consume.FromWorkflow(AgentTeam.class)
  public static class Updater extends TableUpdater<Row> {
    public Effect<Row> onStateChange(AgentTeam.State state) {
      return effects()
          .updateRow(new Row(state.userId(), state.userQuery(), state.finalAnswer()));
    }

    @DeleteHandler
    public Effect<Row> onDelete() {
      return effects().deleteRow();
    }
  }

}
