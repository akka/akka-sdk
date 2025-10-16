<!-- <nav> -->
- [Akka](../../index.html)
- [Tutorials](../index.html)
- [Multi-agent planner](index.html)
- [Orchestrate the agents](team.html)

<!-- </nav> -->

# Orchestrate the agents

|  | **New to Akka? Start here:**

Use the [Build your first agent](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

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

Our workflow should first retrieve the weather forecast and then find suitable activities. Add a new file `AgentTeamWorkflow.java` to `src/main/java/com/example/application/`

AgentTeamWorkflow.java
```java
import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamWorkflow.State> {

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  public record Request(String userId, String message) {}

  public record State(String userId, String userQuery, String finalAnswer) {
    State withAnswer(String a) {
      return new State(userId, userQuery, a);
    }
  }

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(Request request) {
    return effects()
      .updateState(new State(request.userId(), request.message(), "")) // (1)
      .transitionTo(AgentTeamWorkflow::askWeather) // (2)
      .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    if (currentState() == null || currentState().finalAnswer.isEmpty()) {
      String workflowId = commandContext().workflowId();
      return effects()
        .error("Workflow '" + workflowId + "' not started, or not completed");
    } else {
      return effects().reply(currentState().finalAnswer);
    }
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
      .stepTimeout(AgentTeamWorkflow::askWeather, ofSeconds// (60))
      .stepTimeout(AgentTeamWorkflow::suggestActivities, ofSeconds// (60))
      .defaultStepRecovery(maxRetries// (2).failoverTo(AgentTeamWorkflow::error))
      .build();
  }

  @StepName("weather")
  private StepEffect askWeather() { // (3)
    var forecast = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(WeatherAgent::query)
      .invoke(currentState().userQuery);

    logger.info("Weather forecast: {}", forecast);

    return stepEffects()
      .thenTransitionTo(AgentTeamWorkflow::suggestActivities); // (4)
  }

  @StepName("activities")
  private StepEffect suggestActivities() {
    var suggestion = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(ActivityAgent::query) // (5)
      .invoke(new ActivityAgent.Request(currentState().userId(), currentState().userQuery()));

    logger.info("Activities: {}", suggestion);

    return stepEffects()
      .updateState(currentState().withAnswer(suggestion)) // (6)
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

| **1** | The workflow starts, and keeps track of the user id and original query in the state of the workflow. |
| **2** | First step is for the weather forecast. |
| **3** | Weather forecast is retrieved by the `WeatherAgent`, which must extract the location from the user query. |
| **4** | Next step is to find activities. |
| **5** | Request to the `ActivityAgent`. |
| **6** | The final result is stored in the workflow state. |
You might have noticed that we don’t pass the forecast as a parameter to the `ActivityAgent`. How would it then know about the weather? The `WeatherAgent` and `Activity` agents share the same session memory and thereby the `ActivityAgent` will have the weather forecast in the context that is sent to the AI model.

## <a href="about:blank#_adjust_the_endpoint"></a> Adjust the endpoint

Let’s modify the endpoint to use the `AgentTeamWorkflow` workflow instead of calling the agent directly.

ActivityEndpoint.java
```java
@Post("/activities/{userId}")
public HttpResponse suggestActivities(String userId, Request request) {
  var sessionId = UUID.randomUUID().toString();

  var res = componentClient
    .forWorkflow(sessionId)
    .method(AgentTeamWorkflow::start) // (1)
    .invoke(new AgentTeamWorkflow.Request(userId, request.message()));

  return HttpResponses.created(res, "/activities/" + userId + "/" + sessionId); // (2)
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
  var res = componentClient
    .forWorkflow(sessionId)
    .method(AgentTeamWorkflow::getAnswer)
    .invoke();

  if (res.isEmpty()) return HttpResponses.notFound(
    "Answer for '" + sessionId + "' not available (yet)"
  );
  else return HttpResponses.ok(res);
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
  --data '{"message": "I am in Madrid. What should I do? Beware of the weather."}'
```
Retrieve the suggested activities with the `sessionId` from the previous response:

```command
curl -i -XGET --location "http://localhost:9000/activities/alice/{sessionId}"
```
Does it take the current weather forecast into account? You should see the `Weather forecast` in the logs of the service.

## <a href="about:blank#_next_steps"></a> Next steps

- It would be nice to see all previous suggestions for a user. Continue with [List by user](list.html)
- Learn more about the <a href="../../java/workflows.html">`Workflow` component</a>.

<!-- <footer> -->
<!-- <nav> -->
[Weather agent](weather.html) [List by user](list.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->