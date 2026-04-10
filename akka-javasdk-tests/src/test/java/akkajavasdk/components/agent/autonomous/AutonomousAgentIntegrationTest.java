/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.failTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.handoffTo;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.sendTo;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akkajavasdk.components.agent.FactCheckAgent;
import akkajavasdk.components.agent.SomeAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class AutonomousAgentIntegrationTest extends TestKitSupport {

  private final TestModelProvider testAgentModel = new TestModelProvider();
  private final TestModelProvider toolModel = new TestModelProvider();
  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider workerModel = new TestModelProvider();
  private final TestModelProvider triageModel = new TestModelProvider();
  private final TestModelProvider specialistModel = new TestModelProvider();
  private final TestModelProvider requestDelegatingModel = new TestModelProvider();
  private final TestModelProvider someAgentModel = new TestModelProvider();
  private final TestModelProvider factCheckAgentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(TestAutonomousAgent.class, testAgentModel)
        .withModelProvider(ToolUsingAgent.class, toolModel)
        .withModelProvider(CoordinatorAgent.class, coordinatorModel)
        .withModelProvider(WorkerAgent.class, workerModel)
        .withModelProvider(TriageTestAgent.class, triageModel)
        .withModelProvider(SpecialistTestAgent.class, specialistModel)
        .withModelProvider(RequestDelegatingAgent.class, requestDelegatingModel)
        .withModelProvider(SomeAgent.class, someAgentModel)
        .withModelProvider(FactCheckAgent.class, factCheckAgentModel);
  }

  @AfterEach
  public void afterEach() {
    testAgentModel.reset();
    toolModel.reset();
    coordinatorModel.reset();
    workerModel.reset();
    triageModel.reset();
    specialistModel.reset();
    requestDelegatingModel.reset();
    someAgentModel.reset();
    factCheckAgentModel.reset();
  }

  @Test
  public void shouldCompleteTaskWithTypedResult() {
    testAgentModel.fixedResponse(completeTask("{\"value\":\"42 is the answer.\",\"score\":95}"));

    var taskId =
        componentClient
            .forAutonomousAgent(TestAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.TEST_TASK.instructions("What is the meaning of life?"));

    assertThat(taskId).isNotBlank();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().value()).isEqualTo("42 is the answer.");
              assertThat(snapshot.result().score()).isEqualTo(95);
            });
  }

  @Test
  public void shouldCompleteTaskWithStringResult() {
    testAgentModel.fixedResponse(completeTask("{\"result\":\"The capital of France is Paris.\"}"));

    var taskId =
        componentClient
            .forAutonomousAgent(TestAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.STRING_TASK.instructions("What is the capital of France?"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.STRING_TASK);
              assertThat(snapshot.result()).isEqualTo("The capital of France is Paris.");
            });
  }

  @Test
  public void shouldFailTask() {
    testAgentModel.fixedResponse(failTask("Cannot answer this question."));

    var taskId =
        componentClient
            .forAutonomousAgent(TestAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.TEST_TASK.instructions("Unanswerable question"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.status().name()).isEqualTo("FAILED");
              assertThat(snapshot.failureReason()).isEqualTo("Cannot answer this question.");
            });
  }

  @Test
  public void shouldUseToolsThenCompleteTask() {
    // First LLM call: agent invokes the tool
    toolModel
        .whenMessage(msg -> msg.contains("today"))
        .reply(new TestModelProvider.ToolInvocationRequest("DateService_getToday", ""));

    // After tool result, complete the task
    toolModel
        .whenToolResult(result -> result.content().equals("2025-01-15"))
        .thenReply(
            result ->
                new AiResponse(completeTask("{\"value\":\"Today is 2025-01-15.\",\"score\":100}")));

    var taskId =
        componentClient
            .forAutonomousAgent(ToolUsingAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.TEST_TASK.instructions("What is today's date?"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().value()).isEqualTo("Today is 2025-01-15.");
              assertThat(snapshot.result().score()).isEqualTo(100);
            });
  }

  @Test
  public void shouldDelegateToWorkerAndSynthesizeResult() {
    // Coordinator delegates to worker
    coordinatorModel
        .whenMessage(msg -> msg.contains("quantum"))
        .reply(
            delegateTo(
                TestTasks.FINDINGS, WorkerAgent.class, "Research quantum computing fundamentals"));

    // Worker completes with findings
    workerModel.fixedResponse(
        completeTask(
            "{\"topic\":\"Quantum Computing\",\"findings\":\"Qubits enable parallel"
                + " computation.\"}"));

    // Coordinator synthesizes after receiving delegation result
    coordinatorModel
        .whenMessage(msg -> msg.contains("Continue working"))
        .reply(
            completeTask(
                "{\"title\":\"Quantum Computing Summary\",\"summary\":\"Qubits enable parallel"
                    + " computation.\"}"));

    var taskId =
        componentClient
            .forAutonomousAgent(CoordinatorAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.RESEARCH.instructions("Research quantum computing"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.RESEARCH);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().title()).isEqualTo("Quantum Computing Summary");
              assertThat(snapshot.result().summary()).contains("parallel computation");
            });
  }

  @Test
  public void shouldHandoffToSpecialist() {
    // Triage agent classifies as billing and hands off
    triageModel.fixedResponse(
        handoffTo(SpecialistTestAgent.class, "Customer has a billing dispute."));

    // Specialist resolves
    specialistModel.fixedResponse(
        completeTask(
            "{\"category\":\"billing\",\"resolution\":\"Refund issued.\",\"resolved\":true}"));

    var taskId =
        componentClient
            .forAutonomousAgent(TriageTestAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.RESOLVE.instructions("I was charged twice."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.RESOLVE);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().category()).isEqualTo("billing");
              assertThat(snapshot.result().resolved()).isTrue();
            });
  }

  @Test
  public void shouldDelegateToRequestBasedAgent() {
    // Autonomous agent delegates to request-based SomeAgent
    requestDelegatingModel
        .whenMessage(msg -> msg.contains("verify"))
        .reply(sendTo(SomeAgent.class, "mapLlmResponse", "{\"claim\":\"The sky is blue.\"}"));

    // SomeAgent responds
    someAgentModel.fixedResponse("{\"response\":\"Verified: the sky is indeed blue.\"}");

    // Autonomous agent synthesizes after receiving result
    requestDelegatingModel
        .whenMessage(msg -> msg.contains("Continue working"))
        .reply(completeTask("{\"value\":\"Claim verified.\",\"score\":90}"));

    var taskId =
        componentClient
            .forAutonomousAgent(RequestDelegatingAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.TEST_TASK.instructions("Please verify: the sky is blue."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().value()).isEqualTo("Claim verified.");
              assertThat(snapshot.result().score()).isEqualTo(90);
            });
  }

  @Test
  public void shouldReceiveLifecycleNotifications() {
    testAgentModel.fixedResponse(
        new AiResponse(
            "",
            List.of(completeTask("{\"value\":\"done\",\"score\":1}")),
            Optional.of(new Agent.TokenUsage(150, 42))));

    var agentId = UUID.randomUUID().toString();
    var agentClient = componentClient.forAutonomousAgent(TestAutonomousAgent.class, agentId);

    // Subscribe to notifications before triggering the agent
    var notifications = new ArrayList<Notification>();
    agentClient.notificationStream().runForeach(notifications::add, testKit.getMaterializer());

    var taskId =
        agentClient.runSingleTask(TestTasks.TEST_TASK.instructions("Do something simple."));

    // Wait for the task to complete
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
            });

    // Verify we received lifecycle notifications
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(notifications)
                  .anySatisfy(n -> assertThat(n).isInstanceOf(Notification.Activated.class));
              assertThat(notifications)
                  .anySatisfy(n -> assertThat(n).isInstanceOf(Notification.IterationStarted.class));
              assertThat(notifications)
                  .anySatisfy(
                      n -> assertThat(n).isInstanceOf(Notification.IterationCompleted.class));
              assertThat(notifications)
                  .anySatisfy(n -> assertThat(n).isInstanceOf(Notification.Stopped.class));

              // Verify token counts are available on IterationCompleted
              var iterationCompleted =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.IterationCompleted)
                      .map(n -> (Notification.IterationCompleted) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(iterationCompleted.tokenUsage().inputTokens()).isEqualTo(150);
              assertThat(iterationCompleted.tokenUsage().outputTokens()).isEqualTo(42);

              // Verify task lifecycle notifications
              var taskStarted =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.TaskStarted)
                      .map(n -> (Notification.TaskStarted) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(taskStarted.taskId()).isEqualTo(taskId);
              assertThat(taskStarted.taskName()).isNotBlank();

              var taskCompleted =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.TaskCompleted)
                      .map(n -> (Notification.TaskCompleted) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(taskCompleted.taskId()).isEqualTo(taskId);
            });
  }

  @Test
  public void shouldGetAgentState() {
    testAgentModel.fixedResponse(completeTask("{\"value\":\"done\",\"score\":1}"));

    var agentId = UUID.randomUUID().toString();
    var agentClient = componentClient.forAutonomousAgent(TestAutonomousAgent.class, agentId);

    var taskId =
        agentClient.runSingleTask(TestTasks.TEST_TASK.instructions("Do something simple."));

    // Wait for the task to complete
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
            });

    // After completion, query agent state
    var state = agentClient.getState();
    assertThat(state.phase()).isEqualTo("PHASE_STOPPED");
    assertThat(state.paused()).isFalse();
    assertThat(state.totalTokenUsage()).isNotNull();
    assertThat(state.currentTask()).isEmpty();
    assertThat(state.pendingTaskIds()).isEmpty();
  }

  @Test
  public void shouldPauseAndResumeAgent() {
    testAgentModel.fixedResponse(completeTask("{\"value\":\"done after resume\",\"score\":1}"));

    var agentId = UUID.randomUUID().toString();
    var agentClient = componentClient.forAutonomousAgent(TestAutonomousAgent.class, agentId);

    // Pause the agent before assigning any work
    agentClient.pause();

    var state = agentClient.getState();
    assertThat(state.paused()).isTrue();

    // Create a task and assign it — the agent should not process it while paused
    var taskId =
        componentClient
            .forTask(UUID.randomUUID().toString())
            .create(TestTasks.TEST_TASK.instructions("Do something."));
    agentClient.assignTasks(taskId);

    // Resume the agent so it can process the task
    agentClient.resume();

    // Wait for the task to complete after resume
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
            });

    var stateAfterResume = agentClient.getState();
    assertThat(stateAfterResume.paused()).isFalse();
  }

  @Test
  public void shouldReceiveNotificationsOnFailedTask() {
    testAgentModel.fixedResponse(failTask("Something went wrong."));

    var agentId = UUID.randomUUID().toString();
    var agentClient = componentClient.forAutonomousAgent(TestAutonomousAgent.class, agentId);

    var notifications = new ArrayList<Notification>();
    agentClient.notificationStream().runForeach(notifications::add, testKit.getMaterializer());

    var taskId = agentClient.runSingleTask(TestTasks.TEST_TASK.instructions("This will fail."));

    // Wait for the task to fail
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.status().name()).isEqualTo("FAILED");
            });

    // Verify notifications include activation, task failure, and stop
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(notifications)
                  .anySatisfy(n -> assertThat(n).isInstanceOf(Notification.Activated.class));
              assertThat(notifications)
                  .anySatisfy(n -> assertThat(n).isInstanceOf(Notification.Stopped.class));

              var taskStarted =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.TaskStarted)
                      .map(n -> (Notification.TaskStarted) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(taskStarted.taskId()).isEqualTo(taskId);

              var taskFailed =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.TaskFailed)
                      .map(n -> (Notification.TaskFailed) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(taskFailed.taskId()).isEqualTo(taskId);
              assertThat(taskFailed.reason()).isNotBlank();
            });
  }

  @Test
  public void shouldUnwrapDelegatedCommandPayload() {
    // Prefer ToolResult messages so we can match on the delegation response content
    requestDelegatingModel.withMessageSelector(
        messages ->
            messages.stream()
                .filter(m -> m instanceof TestModelProvider.ToolResult)
                .findFirst()
                .orElse(messages.getLast()));

    // LLM tool call args wrap the String parameter under its name: {"question": "..."}
    // The CommandSerialization unwrap fix extracts the value matching the method parameter name
    requestDelegatingModel
        .whenMessage(msg -> msg.contains("verify"))
        .reply(sendTo(SomeAgent.class, "mapLlmResponse", "{\"question\":\"The sky is blue.\"}"));

    // SomeAgent passes the unwrapped question as user message to the model,
    // verify it arrived correctly
    someAgentModel
        .whenMessage(msg -> msg.contains("The sky is blue."))
        .reply("Verified: the sky is indeed blue.");

    // After successful delegation, the tool result contains SomeAgent's response.
    // Match on that content so a failed delegation ("Delegation failed: ...") won't match.
    requestDelegatingModel
        .whenToolResult(result -> result.content().contains("sky is indeed blue"))
        .thenReply(
            result -> new AiResponse(completeTask("{\"value\":\"Claim verified.\",\"score\":90}")));

    var taskId =
        componentClient
            .forAutonomousAgent(RequestDelegatingAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.TEST_TASK.instructions("Please verify: the sky is blue."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().value()).isEqualTo("Claim verified.");
              assertThat(snapshot.result().score()).isEqualTo(90);
            });
  }

  @Test
  public void shouldUnwrapTypedParameterWhenDelegatingToRequestBasedAgent() {
    // Prefer ToolResult messages so we can match on the delegation response content
    requestDelegatingModel.withMessageSelector(
        messages ->
            messages.stream()
                .filter(m -> m instanceof TestModelProvider.ToolResult)
                .findFirst()
                .orElse(messages.getLast()));

    // LLM tool call args wrap the record parameter under its name:
    // {"request": {"claim": "...", "confidence": 95}}
    // The unwrap fix extracts the inner object and deserializes it as FactCheckRequest
    requestDelegatingModel
        .whenMessage(msg -> msg.contains("round"))
        .reply(
            sendTo(
                FactCheckAgent.class,
                "checkFact",
                "{\"request\":{\"claim\":\"The earth is round.\",\"confidence\":95}}"));

    // FactCheckAgent builds its user message from the record fields,
    // verify both fields arrived correctly
    factCheckAgentModel
        .whenMessage(msg -> msg.contains("The earth is round.") && msg.contains("95"))
        .reply("Confirmed: the earth is round.");

    // Match on the successful delegation result — "Confirmed" only appears
    // in FactCheckAgent's model response, not in the initial task instructions
    requestDelegatingModel
        .whenToolResult(result -> result.content().contains("Confirmed"))
        .thenReply(
            result -> new AiResponse(completeTask("{\"value\":\"Fact confirmed.\",\"score\":95}")));

    var taskId =
        componentClient
            .forAutonomousAgent(RequestDelegatingAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.TEST_TASK.instructions("Check: the earth is round."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().value()).isEqualTo("Fact confirmed.");
              assertThat(snapshot.result().score()).isEqualTo(95);
            });
  }
}
