<!-- <nav> -->
- [Akka](../../index.html)
- [Getting Started](../index.html)
- [Build an AI multi-agent planner](index.html)
- [Evaluation on changes](eval.html)

<!-- </nav> -->

# Evaluation on changes

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

We added [User preferences](preferences.html) and those are taken into account when creating the activity suggestions. Now we will use another agent to evaluate if the previous suggestions are still acceptable when the preferences are changed or if new suggestions should be created.

In this part of the guide you will:

- Add an agent that evaluates the quality of the AI answer given the original request and the updated preferences
- Use a Consumer to trigger evaluation when the preferences are changed

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_evaluator_agent"></a> Evaluator agent

We will use a pattern called "LLM as judge", which will use an agent (AI model) to evaluate the result of other agents.

Add a new file `EvaluatorAgent.java` to `src/main/java/com/example/application/`

[EvaluatorAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/EvaluatorAgent.java)

```java
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;

import java.util.stream.Collectors;

@Component(id = "evaluator-agent",
          name = "Evaluator Agent",
          description = """
            An agent that acts as an LLM judge to evaluate the quality of AI responses.
            It assesses whether the final answer is appropriate for the original question
            and checks for any deviations from user preferences.
            """
)
@AgentRole("worker")
public class EvaluatorAgent extends Agent {

  public record EvaluationRequest(String userId, String originalRequest, String finalAnswer) {
  }

  public record EvaluationResult(
    int score,
    String feedback
  ) {
  }

  private static final String SYSTEM_MESSAGE = // (1)
    """
      You are an evaluator agent that acts as an LLM judge. Your job is to evaluate
      the quality and appropriateness of AI-generated responses.
      
      Your evaluation should focus on:
      1. Whether the final answer appropriately addresses the original question
      2. Whether the answer respects and aligns with the user's stated preferences
      3. The overall quality, relevance, and helpfulness of the response
      4. Any potential deviations or inconsistencies with user preferences
      
      SCORING CRITERIA:
      - Use a score from 1-5 where:
        * 5 = Excellent response that fully addresses the question and respects all preferences
        * 4 = Good response with minor issues but respects preferences
        * 3 = Acceptable response that meets basic requirements and respects preferences
        * 2 = Poor response with significant issues or minor preference violations
        * 1 = Unacceptable response that fails to address the question or violates preferences
      
      IMPORTANT:
      - A score of 3 or higher means the answer passes evaluation (acceptable)
      - A score below 3 means the answer fails evaluation (unacceptable)
      - Any violations of user preferences should result in a failing score (below 3) since 
        respecting user preferences is the most important criteria
      
      Your response should be a JSON object with the following structure:
      {
        "score": <integer from 1-5>,
        "feedback": "<specific feedback on what works well or deviations from preferences>",
      }
      
      Do not include any explanations or text outside of the JSON structure.
      """.stripIndent();

  private final ComponentClient componentClient;

  public EvaluatorAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<EvaluationResult> evaluate(EvaluationRequest request) {
    var allPreferences =
      componentClient
        .forEventSourcedEntity(request.userId())
        .method(PreferencesEntity::getPreferences)
        .invoke(); // (2)

    String evaluationPrompt = buildEvaluationPrompt(
      request.originalRequest(),
      request.finalAnswer(),
      allPreferences.entries()
    );

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(evaluationPrompt)
      .responseAs(EvaluationResult.class) // (3)
      .thenReply();
  }

  private String buildEvaluationPrompt(String originalRequest, String finalAnswer, java.util.List<String> preferences) {
    StringBuilder prompt = new StringBuilder();

    prompt.append("ORIGINAL REQUEST:\n")
      .append(originalRequest)
      .append("\n\n");

    prompt.append("FINAL ANSWER TO EVALUATE:\n")
      .append(finalAnswer)
      .append("\n\n");

    if (!preferences.isEmpty()) {
      prompt.append("USER PREFERENCES:\n")
        .append(preferences.stream()
          .collect(Collectors.joining("\n", "- ", "")))
        .append("\n\n");
    }

    prompt.append("Please evaluate the final answer against the original request")
      .append(preferences.isEmpty() ? "." : " and user preferences.");

    return prompt.toString();
  }
}
```

| **1** | Detailed instructions of how to evaluate and especially pay attention to the user preferences. |
| **2** | Retrieve user preferences. |
| **3** | Structured response. |

## <a href="about:blank#_consumer_of_preference_events"></a> Consumer of preference events

The `PreferencesEntity` emits events when the preferences are changed, and we can listen to these events and trigger the `EvaluatorAgent`.

Add a new file `PreferencesConsumer.java` to `src/main/java/com/example/application/`

[PreferencesConsumer.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/PreferencesConsumer.java)
```java
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import demo.multiagent.domain.PreferencesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "preferences-consumer")
@Consume.FromEventSourcedEntity(PreferencesEntity.class) // (1)
public class PreferencesConsumer extends Consumer { // (2)

  private static final Logger logger = LoggerFactory.getLogger(PreferencesConsumer.class);

  private final ComponentClient componentClient;

  public PreferencesConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onPreferenceAdded(PreferencesEvent.PreferenceAdded event) {
    var userId = messageContext().eventSubject().get(); // the entity id
    logger.info("Preference added for user {}: {}", userId, event.preference());

    // Get all activities (sessions) for this user from the ActivityView
    var activities = componentClient
        .forView()
        .method(ActivityView::getActivities)
        .invoke(userId); // (3)

    // Call EvaluatorAgent for each session
    for (var activity : activities.entries()) {
      if (activity.finalAnswer() != null && !activity.finalAnswer().isEmpty()) {
        var evaluationRequest = new EvaluatorAgent.EvaluationRequest(
            userId,
            activity.userQuestion(),
            activity.finalAnswer()
        );

        var evaluationResult = componentClient
            .forAgent()
            .inSession(activity.sessionId())
            .method(EvaluatorAgent::evaluate)
            .invoke(evaluationRequest); // (4)

        logger.info("Evaluation completed for session {}: score={}, feedback='{}'",
            activity.sessionId(),
            evaluationResult.score(),
            evaluationResult.feedback());

        if (evaluationResult.score() < 3) {
          // run the workflow again to generate a better answer

          componentClient
              .forWorkflow(activity.sessionId())
              .method(AgentTeamWorkflow::runAgain) // (5)
              .invoke();

          logger.info("Started workflow {} for user {} to re-answer question: '{}'",
              activity.sessionId(), userId, activity.userQuestion());
        }

      }
    }

    return effects().done();
  }
}
```

| **1** | Consume events from the `PreferencesEntity`. |
| **2** | Extend `Consumer`. |
| **3** | Retrieve previous activity suggestions for the user from the `ActivityView`. |
| **4** | Call the `EvaluatorAgent` for each previous suggestion. |
| **5** | Run the workflow again, using the new preferences when the evaluation score is below 3. |
This would probably be improved in a real application by not evaluating all previous suggestions, but only the ones that are still relevant, e.g. last 3 sorted by timestamp. The evaluations could also be made in parallel by using `invokeAsync` and compose the `CompletionStage` results.

Add the new method to the `AgentTeamWorkflow:

[AgentTeamWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/AgentTeamWorkflow.java)
```java
public Effect<Done> runAgain() {
    if (currentState() != null) {
      return effects()
          .updateState(State.init(currentState().userId(), currentState().userQuery()))
          .transitionTo(SELECT_AGENTS) // (3)
          .thenReply(Done.getInstance());
    } else {
      return effects().error("Workflow '" + commandContext().workflowId() + "' has not been started");
    }
  }
```

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

```command
curl -i -XPOST --location "http://localhost:9000/activities/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Stockholm tomorrow. What should I do?"}'
```
Retrieve the suggested activities:

```command
curl http://localhost:9000/activities/alice | jq
```
If you have don’t `jq` installed you can skip the formatting of the json response with:

```command
curl http://localhost:9000/activities/alice
```
Add some preferences:

```command
curl -i localhost:9000/preferences/alice \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{
    "preference": "I dislike museums."
  }'
```
Inspect the log output from the service and see if the preferences triggered the evaluation and suggesting new activities.

Retrieve the updated activities:

```command
curl http://localhost:9000/activities/alice | jq
```

## <a href="about:blank#_next_steps"></a> Next steps

Congratulations, you have completed the tour of building a multi-agent system. Now you can take your Akka skills to the next level:

- Learn more about the <a href="../../java/consuming-producing.html">`Consumer` component</a>.
- [Deploy to akka.io](../quick-deploy.html)
- **Expand on your own**: Learn more details of the [Akka components](../../java/components/index.html) to enhance your application with additional features.
- **Explore other Akka samples**: Discover more about Akka by exploring [different use cases](../samples.html) for inspiration.

<!-- <footer> -->
<!-- <nav> -->
[Dynamic orchestration](dynamic-team.html) [Additional Samples](../samples.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->