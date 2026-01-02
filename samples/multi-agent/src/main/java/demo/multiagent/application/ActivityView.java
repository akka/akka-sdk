package demo.multiagent.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import java.util.List;
import java.util.Optional;

@Component(id = "activity-view")
public class ActivityView extends View {

  public record ActivityEntries(List<ActivityEntry> entries) {}

  public record ActivityEntry(
    String userId,
    String sessionId,
    String userQuestion,
    String finalAnswer
  ) {
    public boolean hasFinalAnswer() {
      return finalAnswer != null && !finalAnswer.isEmpty();
    }
  }

  @Query("SELECT * AS entries FROM activities WHERE userId = :userId")
  public QueryEffect<ActivityEntries> getActivities(String userId) {
    return queryResult();
  }

  @Consume.FromWorkflow(AgentTeamWorkflow.class)
  public static class Updater extends TableUpdater<ActivityEntry> {

    public Effect<ActivityEntry> onStateChange(AgentTeamWorkflow.State state) {
      var sessionId = updateContext().eventSubject().get(); // the workflow id
      var currentAnswer = rowState() == null ? "" : rowState().finalAnswer;
      if (currentAnswer.equals(state.finalAnswer())) { // avoid updating the state if no relevant changes
        return effects().ignore();
      } else {
        return effects()
            .updateRow(
                new ActivityEntry(state.userId(), sessionId, state.userQuery(), state.finalAnswer())
            );
      }
    }

    @DeleteHandler
    public Effect<ActivityEntry> onDelete() {
      return effects().deleteRow();
    }
  }
}
