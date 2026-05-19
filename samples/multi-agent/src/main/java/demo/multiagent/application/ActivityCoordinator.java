package demo.multiagent.application;

// tag::all[]
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "activity-coordinator",
  description = """
    Coordinates worker agents to suggest real-world activities for a user.
    Decides whether to consult the weather agent, the activity agent, or both,
    and synthesises their results into a single suggestion.
  """
)
public class ActivityCoordinator extends AutonomousAgent { // <1>

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        When delegating to the activity agent, include the userId from the task header
        (the "User: <userId>" line) in the request so the agent can fetch the user's
        preferences.
        """
      ) // <2>
      .capability(TaskAcceptance.of(ActivityTasks.SUGGEST_ACTIVITIES).maxIterationsPerTask(5)) // <3>
      .capability(Delegation.to(WeatherAgent.class, ActivityAgent.class)); // <4>
  }
}
// end::all[]
