<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Orchestrating multiple agents](orchestrating.html)

<!-- </nav> -->

# Orchestrating multiple agents

A single agent performs one well-defined task. Several agents collaborating on a common goal need a **supervisor** that decides which agent runs and in what order. Agents don’t communicate directly with each other; the supervisor handles routing, retries, and recovery so the individual agents stay simple and reusable.

Akka offers two ways to be that supervisor: a [Workflow](../workflows.html) when the sequence of agent calls is fixed in code, or an [Autonomous Agent](../autonomous-agents.html) coordinator when the model decides which agent runs next. Both run on the Akka runtime with the same durable-execution, retry, and audit guarantees.

|  | This page covers workflow-based orchestration of request-based [Agents](../agents.html). For model-driven coordination (delegation, handoff, teams, moderation) without writing a workflow, see [Autonomous Agents](../autonomous-agents.html) and the [coordination patterns](../autonomous-agents/coordination.html).

When to pick which:

  - Pick **workflow orchestration** when the sequence of steps is fixed in code, when each step is at most one model round-trip, when you want explicit per-step retry policies or external compensation, or when the orchestration crosses service boundaries via A2A, ACP, or MCP.
  - Pick an **Autonomous Agent** when the orchestration sequence itself is a model judgment: the model decides what to consult, which specialist to ask, or whether to iterate, based on what previous steps returned.
Both approaches give the same durable-execution, retry, and audit guarantees. The choice rests on whether the sequence is fixed in code or decided by the model. |

## <a href="about:blank#_workflow_driven_orchestration"></a> Workflow-driven orchestration

Let’s first look at how to define a workflow that orchestrates several agents in a predefined steps. It uses both the `WeatherAgent` and the `ActivityAgent`. First it retrieves the weather forecast and then it finds suitable activities.

```java
@Component(id = "agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamWorkflow.State> {

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  public record State(String userQuery, String weatherForecast, String answer) {
    State withWeatherForecast(String f) {
      return new State(userQuery, f, answer);
    }

    State withAnswer(String a) {
      return new State(userQuery, weatherForecast, a);
    }
  }

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(String query) {
    return effects()
      .updateState(new State(query, "", ""))
      .transitionTo(AgentTeamWorkflow::askWeather) // (1)
      .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    if (currentState() == null || currentState().answer.isEmpty()) {
      String workflowId = commandContext().workflowId();
      return effects().error("Workflow '" + workflowId + "' not started, or not completed");
    } else {
      return effects().reply(currentState().answer);
    }
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
      .stepTimeout(AgentTeamWorkflow::askWeather, ofSeconds(60))
      .stepTimeout(AgentTeamWorkflow::suggestActivities, ofSeconds(60))
      .defaultStepRecovery(RecoverStrategy.maxRetries(2).failoverTo(AgentTeamWorkflow::error))
      .build();
  }

  @StepName("weather")
  private StepEffect askWeather() { // (2)
    var forecast = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(WeatherAgent::query)
      .invoke(currentState().userQuery);

    logger.info("Weather forecast: {}", forecast);

    return stepEffects()
      .updateState(currentState().withWeatherForecast(forecast)) // (3)
      .thenTransitionTo(AgentTeamWorkflow::suggestActivities);
  }

  @StepName("activities")
  private StepEffect suggestActivities() {
    var request = // (4)
      currentState().userQuery +
        "\nWeather forecast: " + currentState().weatherForecast;

    var suggestion = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(ActivityAgent::query)
      .invoke(request);

    logger.info("Activities: {}", suggestion);

    return stepEffects()
      .updateState(currentState().withAnswer(suggestion)) // (5)
      .thenEnd();
  }

  private StepEffect error() {
    return stepEffects().thenEnd();
  }

  private String sessionId() {
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
```

| **1** | The workflow starts by asking for the weather forecast. |
| **2** | Weather forecast is retrieved by the `WeatherAgent`, which must extract the location and date from the user query. |
| **3** | The forecast is stored in the state of the workflow. |
| **4** | The forecast is included in the request to the `ActivityAgent`. |
| **5** | The final result is stored in the workflow state. |
In
![steps 4](../../concepts/_images/steps-4.svg)
we explicitly include the forecast in the request to the `ActivityAgent`. That is not strictly necessary because the agents share the same session memory and thereby the `ActivityAgent` will already have the weather forecast in the context that is sent to the AI model.

The workflow will automatically execute the steps in a reliable and durable way. This means that if a call in a step fails, it will be retried until it succeeds or the retry limit of the recovery strategy is reached and separate error handling can be performed. The state machine of the workflow is durable, which means that if the workflow is restarted for some reason it will continue from where it left off, i.e. execute the current non-completed step again.

## <a href="about:blank#_model_driven_orchestration"></a> Model-driven orchestration

When the orchestration sequence itself is a model judgment, the supervisor is an [Autonomous Agent](../autonomous-agents.html) coordinator. The coordinator declares a <a href="../autonomous-agents/capabilities.html#delegation">`Delegation`</a> capability listing the worker agents it may call. The runtime exposes each worker to the coordinator’s model as a tool. The model picks which worker to invoke based on the workers' `@Component` descriptions and what previous calls returned. The runtime persists task state, retries failed steps, and bounds the loop by an iteration limit.

Workers stay as plain request-based [Agents](../agents.html). They don’t know they are being orchestrated; the coordinator handles the routing.

[ActivityCoordinator.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/ActivityCoordinator.java)
```java
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "activity-coordinator",
  description = """
  Coordinates worker agents to suggest real-world activities for a user. \
  Decides whether to consult the weather agent, the activity agent, or both, \
  and synthesizes their results into a single suggestion.\
  """
)
public class ActivityCoordinator extends AutonomousAgent { // (1)

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        When delegating to the activity agent, include the userId from the task header \
        (the "User: <userId>" line) in the request so the agent can fetch the user's \
        preferences.\
        """
      ) // (2)
      .capability(TaskAcceptance.of(ActivityTasks.SUGGEST_ACTIVITIES).maxIterationsPerTask(5)) // (3)
      .capability(Delegation.to(WeatherAgent.class, ActivityAgent.class)); // (4)
  }
}
```

| **1** | Extend `AutonomousAgent`. There is no command handler; the runtime drives the loop until each task completes. |
| **2** | Optional definition-level instructions for guidance specific to this coordinator. Most coordinators don’t need this. |
| **3** | Declare the accepted task type. `maxIterationsPerTask` bounds the model loop as a safety net. |
| **4** | List the workers the coordinator may delegate to. `Delegation.to` accepts both request-based Agents and other Autonomous Agents. |
The model picks workers based on their `@Component` descriptions, so a worker’s description should describe what it is for, not how it is invoked. The `WeatherAgent` advertises itself as the source of weather information:

[WeatherAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/WeatherAgent.java)
```java
@Component(
  id = "weather-agent",
  name = "Weather Agent",
  description = """
  An agent that provides weather information. It can provide current weather, \
  forecasts, and other related information.\
  """
)
public class WeatherAgent extends Agent {
```
The `ActivityAgent` advertises its own purpose the same way, and the coordinator’s model chooses between them per request.

See [Autonomous Agents](../autonomous-agents.html) for more details and the other coordination capabilities (handoff, teams, moderation). The [planner-agent tutorial](../../getting-started/planner-agent/dynamic-team.html) walks through this coordinator end-to-end.

## <a href="about:blank#_combining_the_two_approaches"></a> Combining the two approaches

The two approaches are not mutually exclusive. A workflow can invoke an [Autonomous Agent](../autonomous-agents.html) from any of its steps, the same way it invokes a request-based Agent. This fits scenarios where the overall sequence is fixed but a particular stage benefits from model-driven coordination. For example, a research stage where a coordinator delegates to several specialists, or a review stage that runs as a team or a moderated conversation. The workflow keeps the durable state machine across stages, while the Autonomous Agent handles the elaboration within a stage.

<!-- <footer> -->
<!-- <nav> -->
[Streaming responses](streaming.html) [Guardrails](guardrails.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->