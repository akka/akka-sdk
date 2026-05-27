<!-- <nav> -->
- [Akka](../../index.html)
- [Getting started & Tutorials](../index.html)
- [Multi-agent planner](index.html)
- [Evaluating task results](eval.html)

<!-- </nav> -->

# Evaluating task results

|  | **New to Akka? Start here:**

Use the [Build your first agent with Spec-Driven Development](../spec-your-first-agent.html) guide to use your AI assistant for implementing a simple agentic service, running it locally and interacting with it. |

## <a href="about:blank#_overview"></a> Overview

When a coordinator task completes, we want to know how well the answer holds up. We will use a Consumer that listens for task-completion events from the runtime and runs two evaluators on each result: a custom LLM-as-judge that checks the answer against the original request (including any user preferences carried in it), and the built-in toxicity evaluator.

The verdict from each evaluator is recorded in logs, metrics, and traces.

In this part of the guide you will:

- Add a custom evaluator agent.
- Add a Consumer that subscribes to task-entity events and runs both evaluators when a task completes.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_evaluator_agent"></a> Evaluator agent

We will use a pattern called "LLM as judge", which uses an agent (AI model) to evaluate the result produced by other agents. The evaluator looks at the original request together with the produced answer and returns a typed verdict.

Add a new file `EvaluatorAgent.java` to `src/main/java/com/example/application/`

[EvaluatorAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/EvaluatorAgent.java)
```java
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.EvaluationResult;
import akka.javasdk.annotations.Component;
import java.util.Locale;

@Component(
  id = "evaluator-agent",
  name = "Evaluator Agent",
  description = """
  An agent that acts as an LLM judge to evaluate the quality of AI responses. \
  It assesses whether the final answer appropriately addresses the original request \
  and respects any user preferences carried in the request.\
  """
)
public class EvaluatorAgent extends Agent {

  public record EvaluationRequest(String originalRequest, String finalAnswer) {}

  public record Result(String explanation, String label) implements EvaluationResult {
    public boolean passed() {
      if (label == null) throw new IllegalArgumentException(
        "Model response must include label field"
      );

      return switch (label.toLowerCase(Locale.ROOT)) {
        case "correct" -> true;
        case "incorrect" -> false;
        default -> throw new IllegalArgumentException(
          "Unknown evaluation result [" + label + "]"
        );
      };
    }
  }

  private static final String SYSTEM_MESSAGE =
    """
    You are an evaluator agent that acts as an LLM judge. Your job is to evaluate
    the quality and appropriateness of AI-generated responses.

    The original request may include user preferences. Your evaluation should focus on:
    1. Whether the final answer appropriately addresses the original question
    2. Whether the answer respects and aligns with any stated user preferences
    3. The overall quality, relevance, and helpfulness of the response

    A response is "Incorrect" if it meets ANY of the following failure conditions:
    - poor response with significant issues or minor preference violations
    - unacceptable response that fails to address the question or violates preferences

    A response is "Correct" if it:
    - fully addresses the question and respects all stated preferences
    - good response with minor issues but respects preferences

    IMPORTANT:
    - Any violations of user preferences should result in an Incorrect evaluation since
      respecting user preferences is the most important criteria

    Your response must be a single JSON object with the following fields:
    - "explanation": Specific feedback on what works well or deviations from preferences.
    - "label": A string, either "Correct" or "Incorrect".
    """.stripIndent();

  public Effect<Result> evaluate(EvaluationRequest request) {
    var prompt =
      """
      ORIGINAL REQUEST:
      %s

      FINAL ANSWER TO EVALUATE:
      %s

      Please evaluate the final answer against the original request.
      """.formatted(request.originalRequest(), request.finalAnswer());

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(prompt)
      .responseConformsTo(Result.class)
      .thenReply();
  }
}
```
The evaluator is a request-based [Agent](../../sdk/agents.html), not an `AutonomousAgent`: it does a single model call per evaluation and returns a typed result. There is no iteration loop and no multi-agent coordination, so an ordinary agent is the lighter choice.

## <a href="about:blank#_consumer_of_task_events"></a> Consumer of task events

The runtime stores every coordinator task in a built-in event-sourced task entity (`akka.javasdk.agent.task.TaskEntity`). Subscribing to that entity’s events gives a hook to run on every task transition. We react to `TaskEvent.TaskCompleted`, fetch the task snapshot to get both the original instructions and the produced answer, then run the evaluators.

Add a new file `EvaluationConsumer.java` to `src/main/java/com/example/application/`

[EvaluationConsumer.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/EvaluationConsumer.java)
```java
import akka.javasdk.agent.evaluator.ToxicityEvaluator;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.agent.task.TaskEvent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "evaluation-consumer")
@Consume.FromEventSourcedEntity(TaskEntity.class) // (1)
public class EvaluationConsumer extends Consumer {

  private static final Logger logger = LoggerFactory.getLogger(EvaluationConsumer.class);

  private final ComponentClient componentClient;

  public EvaluationConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(TaskEvent event) {
    if (
      event instanceof TaskEvent.TaskCompleted completed &&
      ActivityTasks.SUGGEST_ACTIVITIES.name().equals(completed.name())
    ) { // (2)
      var taskId = completed.taskId();
      var snapshot = componentClient.forTask(taskId).get(ActivityTasks.SUGGEST_ACTIVITIES); // (3)

      // Custom LLM-as-judge: compare answer against the original (preference-aware) request
      var judgement = componentClient
        .forAgent()
        .inSession(taskId)
        .method(EvaluatorAgent::evaluate)
        .invoke(
          new EvaluatorAgent.EvaluationRequest(
            snapshot.instructions(),
            snapshot.result().orElse("")
          )
        ); // (4)
      if (judgement.passed()) {
        logger.debug("LLM judge passed for task [{}]", taskId);
      } else {
        logger.warn(
          "LLM judge failed for task [{}], explanation: {}",
          taskId,
          judgement.explanation()
        );
      }

      // Built-in toxicity evaluator on the final answer
      var toxicity = componentClient
        .forAgent()
        .inSession(taskId)
        .method(ToxicityEvaluator::evaluate)
        .invoke(snapshot.result().orElse("")); // (5)
      if (toxicity.passed()) {
        logger.debug("Toxicity check passed for task [{}]", taskId);
      } else {
        logger.warn(
          "Toxicity check failed for task [{}], explanation: {}",
          taskId,
          toxicity.explanation()
        );
      }
    }
    return effects().done();
  }
}
```

| **1** | Consume events from the runtime’s built-in task entity. |
| **2** | React only when a `SUGGEST_ACTIVITIES` task completes (the runtime also emits events for create, assign, fail, and cancel). |
| **3** | Fetch a typed snapshot of the task, which exposes the instructions sent to the coordinator and the typed result. |
| **4** | Run the custom LLM-as-judge against the original (preference-aware) request and the produced answer. |
| **5** | Run the built-in toxicity evaluator on the final answer. |
Both verdicts are logged. They also flow into the metrics and traces produced by the runtime, so dashboards and alerting can pick them up without any further wiring.

|  | A single Consumer subscribes to every task in the service. The task `name` can be used to distinguish which kind of task that was changed. |

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
Retrieve the suggestion (use the task id from the response):

```command
curl http://localhost:9000/activities/alice/{taskId}
```
Add a preference that creates a tension with the suggestion:

```command
curl -i localhost:9000/preferences/alice \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{
    "preference": "I dislike outdoor activities."
  }'
```
Submit the same kind of request again (with a fresh session id). Because the endpoint includes the user’s preferences in the task instructions, the coordinator now has the context it needs to produce a different answer. The verdict of the evaluator on the task result shows up in the service logs.

## <a href="about:blank#_next_steps"></a> Next steps

Congratulations, you have completed the tour of building a multi-agent system. Now you can take your Akka skills to the next level:

- Learn more about the <a href="../../sdk/consuming-producing.html">`Consumer` component</a>.
- Explore the other [coordination capabilities](../../sdk/autonomous-agents/capabilities.html) (handoff, teams, moderation) and the [patterns](../../sdk/autonomous-agents/coordination.html) they implement.
- [Deploy to akka.io](../quick-deploy.html)
- **Expand on your own**: Learn more details of the [Akka components](../../sdk/components/index.html) to enhance your application with additional features.
- **Explore other Akka samples**: Discover more about Akka by exploring [different use cases](../samples.html) for inspiration.

<!-- <footer> -->
<!-- <nav> -->
[Dynamic orchestration](dynamic-team.html) [RAG chat agent](../ask-akka-agent/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->