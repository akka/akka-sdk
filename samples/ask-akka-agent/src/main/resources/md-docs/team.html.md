<!-- <nav> -->
- [Akka](../../index.html)
- [Getting Started](../index.html)
- [AI Planner Part 4: Orchestrate the agents](team.html)

<!-- </nav> -->

# AI Planner Part 4: Orchestrate the agents

[1: The activity agent](index.html) > [2: User preferences](preferences.html) > [3: Weather agent](weather.html) > **4: Orchestrate the agents** > [5: List by user](list.html) > > [6: Dynamic orchestration](dynamic-team.html)

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

We have two agents, the `ActivityAgent` and the `WeatherAgent`. We could make a request to the `WeatherAgent` from the endpoint before calling the `ActivityAgent` but a better approach is to introduce a workflow that orchestrates the calls between the agents.

In this part of the guide you will:

- Create a workflow that calls the agents
- Adjust the endpoint to use the workflow

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_orchestrate_the_agents"></a> Orchestrate the agents

Agents make external calls to the AI model and possibly other services, and therefore it is important to have solid error handling and durable execution steps when calling agents. In many cases it is a good recommendation to call agents from a workflow. The workflow will automatically execute the steps in a reliable and durable way. This means that if a call in a step fails, it will be retried until it succeeds or the retry limit of the recovery strategy is reached and separate error handling can be performed. The state machine of the workflow is durable, which means that if the workflow is restarted for some reason it will continue from where it left off, i.e. execute the current non-completed step again.

Our workflow should first retrieve the weather forecast and then find suitable activities. Add a new file `AgentTeam.java` to `src/main/java/com/example/application/`

AgentTeam.java
```java
@ComponentId("agent-team")
public class AgentTeam extends Workflow<AgentTeam.State> {
  private static final Logger logger = LoggerFactory.getLogger(AgentTeam.class);

  public record Request(String userId, String message) {}

  public record State(String userId, String userQuery, String finalAnswer) {
    State withAnswer(String a) {
      return new State(userId, userQuery, a);
    }
  }

  private final ComponentClient componentClient;

  public AgentTeam(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(Request request) {
    return effects()
        .updateState(new State(request.userId(), request.message(), ""))// (1)
        .transitionTo("weather") // (2)
        .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    if (currentState() == null || currentState().finalAnswer.isEmpty()) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started, or not completed");
    } else {
      return effects().reply(currentState().finalAnswer);
    }
  }

  @Override
  public WorkflowDef<State> definition() {
    return workflow()
        .addStep(askWeather())
        .addStep(suggestActivities())
        .addStep(error())
        .defaultStepRecoverStrategy(maxRetries// (2).failoverTo("error"));
  }

  private Step askWeather() { // (3)
    return step("weather")
        .call(() ->
            componentClient
                .forAgent()
                .inSession(sessionId())
                .method(WeatherAgent::query)
                .invoke(currentState().userQuery))
        .andThen(String.class, forecast -> {
          logger.info("Weather forecast: {}", forecast);

          return effects()
              .transitionTo("activities"); // (4)
        })
        .timeout(Duration.ofSeconds// (60));
  }

  private Step suggestActivities() {
    return step("activities")
        .call(() ->
            componentClient
              .forAgent()
              .inSession(sessionId())
              .method(ActivityAgent::query) // (5)
              .invoke(new ActivityAgent.Request(currentState().userId() ,currentState().userQuery())))
        .andThen(String.class, suggestion -> {
          logger.info("Activities: {}", suggestion);

          return effects()
              .updateState(currentState().withAnswer(suggestion)) // (6)
              .end();
        })
        .timeout(Duration.ofSeconds// (60));
  }

  private Step error() {
    return step("error")
        .call(() -> null)
        .andThen(() -> effects().end());
  }

  private String sessionId() {
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
```

| **1** | The workflow starts, and keeps track of the user id and original query in the state of the workflow. |
| **2** | First step is for the weather forecast. |
| **3** | Weather forecast is retrieved by the `WeatherAgent`, which must extract the location from the user query. |
| **4** | Next step is to find activities. |
| **5** | Request to the `ActivityAgent`. |
| **6** | The final result is stored in the workflow state. |
You might have noticed that we don’t pass the forecast as a parameter to the `ActivityAgent`. How would it then know about the weather? The `WeatherAgent` and `Activity agents share the same session memory and thereby the `ActivityAgent` will have the weather forecast in the context that is sent to the AI model.

## <a href="about:blank#_adjust_the_endpoint"></a> Adjust the endpoint

Let’s modify the endpoint to use the `AgentTeam` workflow instead of calling the agent directly.

ActivityEndpoint.java
```java
@Post("/activities/{userId}")
  public HttpResponse suggestActivities(String userId, Request request) {
    var sessionId = UUID.randomUUID().toString();

    var res =
      componentClient
      .forWorkflow(sessionId)
        .method(AgentTeam::start) // (1)
        .invoke(new AgentTeam.Request(userId, request.message()));

    return HttpResponses.created(res, "/activities/ " + userId + "/" + sessionId); // (2)
  }
```

| **1** | Spawn the workflow by calling the `start` method. |
| **2** | Since the workflow is running in the background we can’t wait for the final answer, but instead reply with a HTTP `CREATED` status code. |
This is returning `akka.http.javadsl.model.HttpResponse`, which is created with `akka.javasdk.http.HttpResponses`.

We need another method to retrieve the actual answer:

ActivityEndpoint.java
```java
@Get("/activities/{userId}/{sessionId}")
  public HttpResponse getAnswer(String userId, String sessionId) {
    var res =
        componentClient
            .forWorkflow(sessionId)
            .method(AgentTeam::getAnswer)
            .invoke();

    if (res.isEmpty())
      return HttpResponses.notFound("Answer for '" + sessionId + "' not available (yet)");
    else
      return HttpResponses.ok(res);
  }
```

## <a href="about:blank#_running_the_service"></a> Running the service

Start your service locally:

```command
mvn compile exec:java
```
Ask for activities.

```command
curl -i -XPOST --location "http://localhost:9000/activities/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do?"}'
```
Retrieve the suggested activities with the `sessionId` from the previous response:

```command
curl -i -XGET --location "http://localhost:9000/activities/alice/{sessionId}"
```
Does it take the current weather forecast into account? You should see the `Weather forecast` in the logs of the service.

## <a href="about:blank#_next_steps"></a> Next steps

- It would be nice to see all previous suggestions for a user. Continue with > [Part 5: List by user](list.html)
- Learn more about the <a href="../../java/workflows.html">`Workflow` component</a>.

<!-- <footer> -->
<!-- <nav> -->
[AI Planner Part 3: Weather agent](weather.html) [AI Planner Part 5: List by user](list.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->