/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.failTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akkajavasdk.components.agent.FactCheckAgent;
import akkajavasdk.components.agent.SomeAgent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for basic task lifecycle, tool invocation, and request-based delegation
 * parameter unwrapping.
 */
public class TaskIntegrationTest extends TestKitSupport {

  private final TestModelProvider testAgentModel = new TestModelProvider();
  private final TestModelProvider toolModel = new TestModelProvider();
  private final TestModelProvider requestDelegatingModel = new TestModelProvider();
  private final TestModelProvider someAgentModel = new TestModelProvider();
  private final TestModelProvider factCheckAgentModel = new TestModelProvider();
  private final TestModelProvider templateDelegatingModel = new TestModelProvider();
  private final TestModelProvider teamWorkerModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(TestAutonomousAgent.class, testAgentModel)
        .withModelProvider(ToolUsingAgent.class, toolModel)
        .withModelProvider(RequestDelegatingAgent.class, requestDelegatingModel)
        .withModelProvider(SomeAgent.class, someAgentModel)
        .withModelProvider(FactCheckAgent.class, factCheckAgentModel)
        .withModelProvider(TemplateDelegatingAgent.class, templateDelegatingModel)
        .withModelProvider(TeamWorkerAgent.class, teamWorkerModel);
  }

  @AfterEach
  public void afterEach() {
    testAgentModel.reset();
    toolModel.reset();
    requestDelegatingModel.reset();
    someAgentModel.reset();
    factCheckAgentModel.reset();
    templateDelegatingModel.reset();
    teamWorkerModel.reset();
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
    toolModel
        .whenMessage(msg -> msg.contains("today"))
        .reply(new TestModelProvider.ToolInvocationRequest("DateService_getToday", ""));

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
  public void shouldUnwrapDelegatedCommandPayload() {
    requestDelegatingModel.withMessageSelector(
        messages ->
            messages.stream()
                .filter(m -> m instanceof TestModelProvider.ToolResult)
                .findFirst()
                .orElse(messages.getLast()));

    requestDelegatingModel
        .whenMessage(msg -> msg.contains("verify"))
        .reply(delegateTo(SomeAgent.class, "{\"question\":\"The sky is blue.\"}"));

    someAgentModel
        .whenMessage(msg -> msg.contains("The sky is blue."))
        .reply("Verified: the sky is indeed blue.");

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
    requestDelegatingModel.withMessageSelector(
        messages ->
            messages.stream()
                .filter(m -> m instanceof TestModelProvider.ToolResult)
                .findFirst()
                .orElse(messages.getLast()));

    requestDelegatingModel
        .whenMessage(msg -> msg.contains("round"))
        .reply(
            delegateTo(
                FactCheckAgent.class,
                "{\"request\":{\"claim\":\"The earth is round.\",\"confidence\":95}}"));

    factCheckAgentModel
        .whenMessage(msg -> msg.contains("The earth is round.") && msg.contains("95"))
        .reply("Confirmed: the earth is round.");

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

  @Test
  public void shouldDelegateWithTaskTemplate() {
    // Coordinator delegates using TaskTemplate with template parameters
    templateDelegatingModel
        .whenMessage(msg -> msg.contains("Login"))
        .reply(
            delegateTo(
                TestTasks.WORK_ITEM,
                TeamWorkerAgent.class,
                Map.of("item", "Login page", "requirements", "OAuth support")));

    // Worker completes the delegated work item
    teamWorkerModel.fixedResponse(
        completeTask("{\"item\":\"Login page\",\"output\":\"Implemented OAuth login flow.\"}"));

    // Coordinator synthesizes worker result into research result
    templateDelegatingModel
        .whenMessage(msg -> msg.contains("Continue working"))
        .reply(
            completeTask(
                "{\"title\":\"Login Feature\",\"summary\":\"OAuth login flow implemented.\"}"));

    var taskId =
        componentClient
            .forAutonomousAgent(TemplateDelegatingAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.RESEARCH.instructions("Implement Login page with OAuth"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.RESEARCH);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().title()).isEqualTo("Login Feature");
              assertThat(snapshot.result().summary()).contains("OAuth");
            });
  }
}
