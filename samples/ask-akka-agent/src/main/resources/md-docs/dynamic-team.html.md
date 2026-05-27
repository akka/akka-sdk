<!-- <nav> -->
- [Akka](../../index.html)
- [Getting started & Tutorials](../index.html)
- [Multi-agent planner](index.html)
- [Dynamic orchestration](dynamic-team.html)

<!-- </nav> -->

# Dynamic orchestration

|  | **New to Akka? Start here:**

Use the [Build your first agent with Spec-Driven Development](../spec-your-first-agent.html) guide to use your AI assistant for implementing a simple agentic service, running it locally and interacting with it. |

## <a href="about:blank#_overview"></a> Overview

We have used a workflow with predefined steps to call the `WeatherAgent` followed by the `ActivityAgent`. In a larger system there can be many agents, and it would be cumbersome to define a single workflow for every kind of request. A more flexible approach is to let the AI model decide which agents to consult and in what order, based on the request.

Akka offers an [Autonomous Agent](../../sdk/autonomous-agents.html) for exactly this case. Instead of writing the orchestration sequence as workflow steps, you declare a coordinator agent and list the worker agents it may delegate to. The runtime exposes the workers to the coordinator’s model as tools, drives the iteration loop, persists task state, and retries failed steps. The model picks which worker runs next based on what previous steps returned.

In this part of the guide you will:

- Add a coordinator `AutonomousAgent` with a `Delegation` capability that lists `WeatherAgent` and `ActivityAgent` as workers.
- Define a `SUGGEST_ACTIVITIES` task type with a typed result.
- Replace the workflow in the endpoint with a direct call to the coordinator’s task.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_define_the_task"></a> Define the task

The coordinator works on typed tasks. The task definition is a stable static constant. Add a new file `ActivityTasks.java` to `src/main/java/com/example/application/`

[ActivityTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/ActivityTasks.java)
```java
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
```
The default result type for a task is `String`, which is what we use here. Replace `String` with a record and define `resultConformsTo(MyRecord.class)` if you need a structured result.

## <a href="about:blank#_the_coordinator_agent"></a> The coordinator agent

The coordinator extends `AutonomousAgent`. It declares which task types it accepts and which worker agents it may delegate to. There is no command handler, the runtime drives the agent through its decision loop until the task completes.

Add a new file `ActivityCoordinator.java` to `src/main/java/com/example/application/`

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

| **1** | Extend `AutonomousAgent`. The `@Component` description tells the model what kind of work this agent does. |
| **2** | Optional definition-level instructions. Use these for guidance that is specific to the coordinator’s runtime behavior, here, lifting the userId from the task header into the `ActivityAgent` delegation request. |
| **3** | Declare which task types this agent accepts. `maxIterationsPerTask` bounds the model loop as a safety net. |
| **4** | List the worker agents the coordinator may delegate to. `Delegation.to` accepts both request-based agents and other autonomous agents. |
The task definition itself spells out the expected shape of the instructions string (the `User: <userId>` header followed by the user’s question), so the structural fact lives next to the task and the behavioral instruction lives next to the coordinator. The model in the coordinator sees the descriptions on `WeatherAgent` and `ActivityAgent` (from their `@Component` annotations) as part of its tool catalog, and decides per request which workers to call based on the task instructions and worker descriptions.

## <a href="about:blank#_worker_agents_stay_request_based"></a> Worker agents stay request-based

`WeatherAgent` and `ActivityAgent` remain the plain `Agent` subclasses from earlier parts of this guide, no signature changes are needed. The runtime introspects each worker’s command handler, generates a JSON schema from its parameter type, and exposes a delegate tool to the coordinator’s model. When the model invokes the tool, the runtime deserializes the arguments and calls the worker’s command handler.

In this guide the two workers have different parameter shapes, and the runtime handles both. `WeatherAgent.query` takes a `String`, so the model passes a plain string. `ActivityAgent.query` takes an `AgentRequest` record so the agent can fetch the user’s preferences itself, so the model passes a `{userId, message}` object.

## <a href="about:blank#_replace_the_workflow_in_the_endpoint"></a> Replace the workflow in the endpoint

With the coordinator in place we no longer need the `AgentTeamWorkflow`. The endpoint can drive the coordinator directly: call `runSingleTask` on a fresh coordinator instance with the user message prefixed by a `User: <userId>` header as the task instructions, and let the runtime run the model loop until the task completes. The `ActivityAgent` will fetch the user’s preferences itself using the userId. The `GET` route reads the typed result through the task client.

Update `ActivityEndpoint`:

[ActivityEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/api/ActivityEndpoint.java)
```java
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
    var instructions = "User: " + userId + "\n\n" + request.message(); // (1)

    var taskId = componentClient
      .forAutonomousAgent(ActivityCoordinator.class, UUID.randomUUID().toString())
      .runSingleTask(ActivityTasks.SUGGEST_ACTIVITIES.instructions(instructions)); // (2)

    return HttpResponses.created(taskId, "/activities/" + userId + "/" + taskId);
  }

  @Get("/activities/{userId}/{taskId}")
  public HttpResponse getAnswer(String userId, String taskId) {
    var snapshot = componentClient.forTask(taskId).get(ActivityTasks.SUGGEST_ACTIVITIES); // (3)

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
```

| **1** | Prepend a `User: <userId>` header to the user’s message. This becomes the task instructions the coordinator’s model sees, so it can lift the userId into the `ActivityAgent` delegation. |
| **2** | `runSingleTask` creates the task, assigns it to a fresh coordinator instance, and returns the task id. The runtime starts the agent and drives its decision loop until the task completes. |
| **3** | Read the typed result through the task client. Returns the answer if the task has completed, or `404` while it is still running. |

## <a href="about:blank#_running_the_service"></a> Running the service

Start your service locally:

```command
mvn compile exec:java
```
Ask for activities:

```command
curl -i -XPOST --location "http://localhost:9000/activities/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do? Beware of the weather."}'
```
The response body and `Location` header carry the task id. Retrieve the suggestion:

```command
curl -i -XGET --location "http://localhost:9000/activities/alice/{taskId}"
```
Each worker logs when it is invoked, so the service logs reveal which agents the coordinator chose to consult. For this request the coordinator should call both the `WeatherAgent` and the `ActivityAgent`. Try a request where weather isn’t relevant, for example:

```command
curl -i -XPOST --location "http://localhost:9000/activities/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "What is the best Italian restaurant in London?"}'
```
Inspect the logs again. The coordinator should now skip the `WeatherAgent` and go straight to the `ActivityAgent`, because the request gives the model no reason to consult the weather.

## <a href="about:blank#_next_steps"></a> Next steps

- The coordinator currently uses the `Delegation` capability. [Coordination capabilities](../../sdk/autonomous-agents/capabilities.html) also covers handoff, teams, and moderation. The [coordination patterns](../../sdk/autonomous-agents/coordination.html) page explains when each pattern is the right fit.
- Continue with [Evaluating task results](eval.html) to add an evaluation Consumer that judges each task’s result on completion, illustrating the Consumer component and "LLM as judge" pattern.

<!-- <footer> -->
<!-- <nav> -->
[Orchestrate the agents](team.html) [Evaluating task results](eval.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->