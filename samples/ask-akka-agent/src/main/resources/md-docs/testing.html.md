<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Autonomous Agents](../autonomous-agents.html)
- [Testing](testing.html)

<!-- </nav> -->

# Testing

Autonomous agents are tested using `TestModelProvider` to mock model responses, following the same pattern as request-based agents (see [Testing the agent](../agents/testing.html)). The integration test:

- extends `TestKitSupport`,
- registers a `TestModelProvider` per agent class,
- uses `Awaitility` to poll for asynchronous task completion.

## <a href="about:blank#_single_agent_test"></a> Single-agent test

The simplest case is one agent, one task, one mocked response. The mocked response invokes the built-in `complete_task` tool with a result that matches the task’s result type. Use `AutonomousAgentTools.completeTask(…​)` to construct the call without referencing the internal tool name.

[QuestionAnswererIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/test/java/demo/helloworld/QuestionAnswererIntegrationTest.java)
```java
public class QuestionAnswererIntegrationTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider(); // (1)

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(QuestionAnswerer.class, model); // (2)
  }

  @Test
  public void shouldAnswerQuestionWithTypedResult() {
    model.fixedResponse( // (3)
      new TestModelProvider.AiResponse(completeTask(new Answer("2 plus 2 equals 4.", 100)))
    );

    var response = httpClient // (4)
      .POST("/questions")
      .withRequestBody(new QuestionEndpoint.AskQuestion("What is 2 + 2?"))
      .responseBodyAs(QuestionEndpoint.QuestionResponse.class)
      .invoke()
      .body();

    var taskId = response.id();
    assertThat(taskId).isNotBlank();
    assertThat(response.runId()).isNotBlank();
    assertThat(response.agentComponentId()).isEqualTo("question-answerer");

    Awaitility.await() // (5)
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(QuestionTasks.ANSWER);
        var result = snapshot.result().orElseThrow();
        assertThat(result.answer()).isEqualTo("2 plus 2 equals 4.");
        assertThat(result.confidence()).isEqualTo(100);
      });
  }
```

| **1** | Create a `TestModelProvider` instance as a field of the test class. |
| **2** | Register it in `testKitSettings()` with `.withModelProvider(AgentClass.class, model)`. |
| **3** | Use `.fixedResponse()` to control what the model returns. `completeTask(…​)` builds a tool invocation for the built-in `complete_task` tool, serializing the result object to match the task’s result type. |
| **4** | Trigger the agent through your endpoint. |
| **5** | Poll for the typed result with `Awaitility.await()` because execution is asynchronous. |

## <a href="about:blank#_autonomousagenttools_helpers"></a> AutonomousAgentTools helpers

The `TestModelProvider.AutonomousAgentTools` class provides factory methods for the built-in coordination tools that the runtime exposes to the model. Always prefer these over raw `ToolInvocationRequest` instances with string tool names: the helpers serialize result objects to JSON, derive the right tool name from the agent’s component id and the task definition, and stay correct if the runtime renames a tool.

**Task lifecycle tools:**

```java
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.*;

// Complete a task: pass a result object matching the task's result type
completeTask(new Answer("2 plus 2 equals 4.", 100));

// Complete a task with raw JSON (must be a valid JSON object)
completeTaskJson("{\"answer\":\"2 plus 2 equals 4.\",\"confidence\":100}");

// Fail a task with a reason
failTask("Not enough information to proceed.");
```
**Handoff and delegation tools:**

```java
// Hand off the current task to another agent
handoffTo(BillingSpecialist.class, "Customer has billing dispute");

// Delegate a subtask to a worker agent
delegateTo(ResearchTasks.FINDINGS, Researcher.class, "Research quantum computing");

// Delegate to a request-based agent
delegateTo(FactCheckAgent.class, "{\"claim\":\"Carbon emissions reduced by 40%\"}");
```
`AutonomousAgentTools` also exposes helpers for the team, backlog, messaging, and moderation capabilities (`createTeam`, `claimTask`, `sendMessage`, `startScriptedConversation`, `submitTurn`, …). For full coverage, see the [autonomous agents samples](../../getting-started/samples.html#autonomous_agents_playground) that exercise those patterns: `devteam` for teams and shared backlogs, and `debate`, `negotiation`, `peerreview` for moderation.

## <a href="about:blank#_multi_agent_delegation_test"></a> Multi-agent delegation test

When multiple agents collaborate, register a `TestModelProvider` for each. The example below mocks a coordinator that delegates to two workers and then synthesizes their results.

The setup registers one provider per participating agent class:

[ResearchDelegationIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/test/java/demo/research/ResearchDelegationIntegrationTest.java)
```java
public class ResearchDelegationIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider researcherModel = new TestModelProvider();
  private final TestModelProvider analystModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(ResearchCoordinator.class, coordinatorModel)
      .withModelProvider(Researcher.class, researcherModel)
      .withModelProvider(Analyst.class, analystModel);
  }
```
The test mocks the coordinator’s delegation, the two workers' completions, and the coordinator’s final synthesis:

[ResearchDelegationIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/test/java/demo/research/ResearchDelegationIntegrationTest.java)
```java
@Test
public void shouldDelegateToWorkersAndSynthesizeResult() {
  // Coordinator delegates to both workers
  coordinatorModel
    .whenMessage(msg -> msg.contains("quantum computing"))
    .reply(
      List.of(
        delegateTo(
          ResearchTasks.FINDINGS,
          Researcher.class,
          "Research quantum computing fundamentals"
        ),
        delegateTo(
          ResearchTasks.ANALYSIS,
          Analyst.class,
          "Analyse quantum computing market trends"
        )
      )
    );

  // Researcher completes with ResearchFindings
  researcherModel.fixedResponse(
    new TestModelProvider.AiResponse(
      completeTask(
        new ResearchFindings(
          "Quantum Computing",
          List.of("Qubits enable parallel computation", "Error correction is advancing"),
          List.of("Nature Physics 2024", "IBM Research")
        )
      )
    )
  );

  // Analyst completes with AnalysisReport
  analystModel.fixedResponse(
    new TestModelProvider.AiResponse(
      completeTask(
        new AnalysisReport(
          "Quantum Computing",
          "Market growing rapidly with key players investing heavily.",
          List.of("$50B market by 2030", "Cloud quantum access expanding")
        )
      )
    )
  );

  // Coordinator synthesizes after both workers complete
  coordinatorModel
    .whenMessage(msg -> msg.contains("Continue working"))
    .reply(
      completeTask(
        new ResearchBrief(
          "Quantum Computing Brief",
          "Quantum computing leverages qubits for parallel computation with a rapidly growing market projected at $50B by 2030.",
          List.of(
            "Qubits enable parallel computation",
            "Error correction is advancing",
            "$50B market by 2030",
            "Cloud quantum access expanding"
          )
        )
      )
    );

  var response = httpClient
    .POST("/research")
    .withRequestBody(new ResearchEndpoint.ResearchRequest("quantum computing"))
    .responseBodyAs(ResearchEndpoint.ResearchResponse.class)
    .invoke()
    .body();

  var taskId = response.id();
  assertThat(taskId).isNotBlank();

  Awaitility.await()
    .ignoreExceptions()
    .atMost(30, TimeUnit.SECONDS)
    .untilAsserted(() -> {
      var snapshot = componentClient.forTask(taskId).get(ResearchTasks.BRIEF);
      var result = snapshot.result().orElseThrow();
      assertThat(result.title()).isEqualTo("Quantum Computing Brief");
      assertThat(result.keyFindings()).hasSize(4);
    });
}
```
Notice how `whenMessage(…​).reply(…​)` lets you condition on the prompt content. The coordinator gets a different reply for the initial topic prompt and the post-delegation `Continue working` prompt.

## <a href="about:blank#_testing_a_handoff_flow"></a> Testing a handoff flow

For a handoff, the triage agent’s mocked response calls `handoffTo`, and the specialist’s mocked response calls `completeTask`:

[HandoffIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/test/java/demo/support/HandoffIntegrationTest.java)
```java
@Test
public void shouldHandoffToBillingSpecialist() {
  // Triage agent classifies as billing and hands off
  triageModel.fixedResponse(
    new TestModelProvider.AiResponse(
      handoffTo(
        BillingSpecialist.class,
        "Customer has a billing dispute about double charge on invoice #1234."
      )
    )
  );

  // Billing specialist resolves the issue
  billingModel.fixedResponse(
    new TestModelProvider.AiResponse(
      completeTask(
        new SupportResolution(
          "billing",
          "Refund issued for duplicate charge on invoice #1234.",
          true
        )
      )
    )
  );

  var response = httpClient
    .POST("/support")
    .withRequestBody(
      new SupportEndpoint.SupportRequest(
        "I was charged twice on invoice #1234, please fix this."
      )
    )
    .responseBodyAs(SupportEndpoint.SupportResponse.class)
    .invoke()
    .body();

  var taskId = response.id();
  assertThat(taskId).isNotBlank();

  Awaitility.await()
    .ignoreExceptions()
    .atMost(30, TimeUnit.SECONDS)
    .untilAsserted(() -> {
      var snapshot = componentClient.forTask(taskId).get(SupportTasks.RESOLVE);
      var result = snapshot.result().orElseThrow();
      assertThat(result.category()).isEqualTo("billing");
      assertThat(result.resolved()).isTrue();
    });
}
```

## <a href="about:blank#_scripting_tool_use"></a> Scripting tool use

When the model should call a domain tool, use `whenMessage` to script the first call and `whenToolResult` to react after the tool returns. The follow-up reaction can be another tool call, a delegation, a handoff, or a `completeTask`:

[ConsultingIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/test/java/demo/consulting/ConsultingIntegrationTest.java)
```java
// Coordinator: assess → check complexity → delegate research → synthesise
coordinatorModel
  .whenMessage(msg -> msg.contains("supply chain"))
  .reply(
    List.of(
      new TestModelProvider.ToolInvocationRequest(
        "ConsultingTools_assessProblem",
        "{\"problemDescription\":\"supply chain efficiency\"}"
      )
    )
  );

coordinatorModel
  .whenToolResult(tr -> tr.name().equals("ConsultingTools_assessProblem"))
  .reply(
    new TestModelProvider.ToolInvocationRequest(
      "ConsultingTools_checkComplexity",
      "{\"assessment\":\"moderate complexity, integration challenges\"}"
    )
  );

coordinatorModel
  .whenToolResult(tr -> tr.name().equals("ConsultingTools_checkComplexity"))
  .reply(
    delegateTo(
      ConsultingTasks.RESEARCH,
      ConsultingResearcher.class,
      "Research supply chain optimization best practices"
    )
  );
```
The domain tool name passed to `ToolInvocationRequest` (for example `ConsultingTools_assessProblem`) is the tool name exposed to the model by the agent’s `@FunctionTool` methods, not a built-in runtime tool. Built-in tools are constructed with the `AutonomousAgentTools` helpers instead.

Call `model.reset()` in `@AfterEach` to clear all configured responses between tests when you have multiple test methods sharing the same providers.

## <a href="about:blank#_testing_failure_paths"></a> Testing failure paths

The runtime treats `fail_task` as a terminal failure for the task. Use `failTask` to mock it and test the failure handling in your endpoints:

[QuestionAnswererIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/test/java/demo/helloworld/QuestionAnswererIntegrationTest.java)
```java
@Test
public void shouldFailTaskWhenModelCallsFailTask() {
  model.fixedResponse(
    new TestModelProvider.AiResponse(failTask("I cannot answer this question."))
  );

  var response = httpClient
    .POST("/questions")
    .withRequestBody(new QuestionEndpoint.AskQuestion("What is the meaning of life?"))
    .responseBodyAs(QuestionEndpoint.QuestionResponse.class)
    .invoke()
    .body();

  var taskId = response.id();

  Awaitility.await()
    .ignoreExceptions()
    .atMost(10, TimeUnit.SECONDS)
    .untilAsserted(() -> {
      var snapshot = componentClient.forTask(taskId).get(QuestionTasks.ANSWER);
      assertThat(snapshot.status().name()).isEqualTo("FAILED");
      assertThat(snapshot.failureReason()).contains("I cannot answer this question.");
    });
}
```

## <a href="about:blank#_see_also"></a> See Also

- [Testing request-based agents](../agents/testing.html)
- [Client API](client.html)
- [Notifications](notifications.html) (subscribe to the notification stream in tests to assert on intermediate transitions)

<!-- <footer> -->
<!-- <nav> -->
[Notifications](notifications.html) [Event Sourced Entities](../event-sourced-entities.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->