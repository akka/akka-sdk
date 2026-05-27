package demo.multiagent.application;

// tag::all[]
import akka.javasdk.agent.task.Task;

public class ActivityTasks {

  public static final Task<String> SUGGEST_ACTIVITIES = Task.name(
    "SuggestActivities"
  ).description(
    """
    Suggest real-world activities for a user, taking weather and any stated preferences \
    into account. The task instructions begin with a "User: <userId>" line followed by \
    the user's question.\
    """
  );
}
// end::all[]
