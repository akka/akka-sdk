<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Calling agents](calling.html)

<!-- </nav> -->

# Calling agents

Use the `ComponentClient` to call the agent from a Workflow, Endpoint or Consumer.

```java
var sessionId = UUID.randomUUID().toString();
String suggestion = componentClient
  .forAgent() // (1)
  .inSession(sessionId) // (2)
  .method(ActivityAgent::query)
  .invoke("Business colleagues meeting in London");
```

| **1** | Use `forAgent`. |
| **2** | Define the identifier of the session that the agent participates in. |
The session id is used by the [session memory](memory.html), but it is also important for observability tracking and AI evaluation.

You can use a new random UUID for each call if the agent doesn’t collaborate with other agents nor have a multi-step interaction with the AI model. Deciding how you manage sessions will be an important part of designing the agentic parts of your application.

For more details about the `ComponentClient`, see [Component and service calls](../component-and-service-calls.html).

## <a href="about:blank#_drive_the_agent_from_a_workflow"></a> Drive the agent from a workflow

Agents make external calls to the AI model and possibly other services, and therefore it is important to have solid error handling and durable execution steps when calling agents. In many cases it is a good recommendation to call agents from a [Workflow](../workflows.html). The workflow will automatically execute the steps in a reliable and durable way. This means that if a call in a step fails, it will be retried until it succeeds or the retry limit of the recovery strategy is reached and separate error handling can be performed. The state machine of the workflow is durable, which means that if the workflow is restarted for some reason it will continue from where it left off, i.e. execute the current non-completed step again.

A workflow will typically orchestrate several agents, which collaborate in achieving a common goal. Even if you only have a single agent, having a workflow manage retries, failures, and timeouts can be invaluable.

We will look more at [multi-agent systems](orchestrating.html), but let’s start with a workflow for the single activities agent.

[ActivityAgentManager.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/ActivityAgentManager.java)
```java
@Component(id = "activity-agent-manager")
public class ActivityAgentManager extends Workflow<ActivityAgentManager.State> { // (1)


  public record State(String userQuery, String answer) { // (2)
    State withAnswer(String a) {
      return new State(userQuery, a);
    }
  }

  private final ComponentClient componentClient;

  public ActivityAgentManager(ComponentClient componentClient) { // (3)
    this.componentClient = componentClient;
  }

  public Effect<Done> start(String query) { // (4)
    return effects()
      .updateState(new State(query, ""))
      .transitionTo(ActivityAgentManager::suggestActivities)
      .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<String> getAnswer() { // (5)
    if (currentState() == null || currentState().answer.isEmpty()) {
      String workflowId = commandContext().workflowId();
      return effects()
        .error("Workflow '" + workflowId + "' not started, or not completed");
    } else {
      return effects().reply(currentState().answer);
    }
  }

  @Override
  public WorkflowSettings settings() { // (6)
    return WorkflowSettings.builder()
      .stepTimeout(ActivityAgentManager::suggestActivities, ofSeconds(60))
      .defaultStepRecovery(
        RecoverStrategy.maxRetries(2).failoverTo(ActivityAgentManager::error)
      )
      .build();
  }

  @StepName("activities")
  private StepEffect suggestActivities() { // (7)
    var suggestion = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(ActivityAgent::query) // (8)
      .invoke(currentState().userQuery);

    logger.info("Activities: {}", suggestion);

    return stepEffects()
      .updateState(currentState().withAnswer(suggestion)) // (9)
      .thenEnd();
  }

  private StepEffect error() {
    return stepEffects().thenEnd();
  }

  private String sessionId() { // (10)
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
```

| **1** | Extend `Workflow`. |
| **2** | The state can hold intermediate and final results, and it is durable. |
| **3** | Inject the `ComponentClient`, which will be used when calling the agent. |
| **4** | This workflow only has two command handler methods. One that starts the workflow with the initial user request, |
| **5** | and one to retrieve the final answer. |
| **6** | Define the workflow configuration. |
| **7** | The step that calls the `ActivityAgent` |
| **8** | Call the agent with the `ComponentClient` |
| **9** | Store the result from the agent. |
| **10** | The workflow corresponds to an agent session. |
The workflow itself will be instantiated by making a call to the `start` method from an endpoint or a consumer.

Keep in mind that AI requests are typically slow (many seconds), and you need to define the workflow timeouts accordingly. This is specified in the workflow step definition with:

```java
.stepConfig(ActivityAgentManager::suggestActivities, ofSeconds(60))
```
Additionally, you should define a workflow recovery strategy so that it doesn’t retry failing requests infinitely. This is specified in the workflow definition with:

```java
.defaultStepRecovery(RecoverStrategy.maxRetries(2).failoverTo(ActivityAgentManager::error))
```
More details in [Workflow timeouts and recovery strategy](../workflows.html#_error_handling).

### <a href="about:blank#_human_in_the_loop"></a> Human in the loop

You often need a human-in-the-loop to integrate human oversight into the AI’s decision-making process. A workflow can be paused, waiting for user input. When the approval command is received, the workflow can continue from where it left off and transition to the next step in the agentic process.

See [how to pause a workflow](../workflows.html#_pausing_workflow).

## <a href="about:blank#_calling_agents_by_id"></a> Calling agents by id

In most cases you call an agent by passing its class to the `ComponentClient`, which gives you compile-time type safety on the method, parameters, and return type. When the agent to call is only known at runtime, for example when a planner agent has selected which specialist to invoke, use `dynamicCall(agentId)` instead:

```java
String response = componentClient
  .forAgent()
  .inSession(sessionId)
  .<String, String>dynamicCall(agentId)
  .invoke(request);
```
The call targets the agent whose component id matches `agentId`. There is no compile-time check that the agent’s command handler accepts `request` or returns the expected type; a mismatch surfaces as a runtime exception.

To discover which agents are available, inject the `AgentRegistry`. The registry exposes each agent’s id, name, description, and role (sourced from `@Component` and `@AgentRole`), and supports lookups by id or by role. This is useful when a planner agent decides which specialists to invoke based on the request and then dispatches to them by id:

```java
private final Set<AgentRegistry.AgentInfo> workers;

public MyPlanner(AgentRegistry registry) {
  this.workers = registry.agentsWithRole("worker");
  // include worker descriptions in the planner's prompt
}
```
Dynamic dispatch and the registry are building blocks for planner-driven multi-agent systems. For most multi-agent problems an [Autonomous Agent](../autonomous-agents.html) coordinator with a `Delegation` capability is simpler and recommended; see [Model-driven orchestration](orchestrating.html#_model_driven_orchestration). Reach for `AgentRegistry` and `dynamicCall` when you need a custom planner that selects targets at runtime from an open-ended set of agents.

<!-- <footer> -->
<!-- <nav> -->
[Choosing the prompt](prompt.html) [Managing session memory](memory.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->