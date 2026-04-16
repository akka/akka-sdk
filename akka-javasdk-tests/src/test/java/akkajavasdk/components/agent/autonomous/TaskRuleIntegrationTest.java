/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.task.TaskStatus;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class TaskRuleIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(ValidatedTaskAgent.class, agentModel)
        .withModelProvider(TestAutonomousAgent.class, new TestModelProvider());
  }

  @AfterEach
  public void afterEach() {
    agentModel.reset();
  }

  @Test
  public void shouldCompleteTaskWhenRuleAccepts() {
    agentModel.fixedResponse(completeTask(new TestTasks.TestResult("good result", 50)));

    var taskId =
        componentClient
            .forAutonomousAgent(ValidatedTaskAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.VALIDATED_TASK.instructions("Do something well."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.VALIDATED_TASK);
              assertThat(snapshot.status()).isEqualTo(TaskStatus.COMPLETED);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().value()).isEqualTo("good result");
              assertThat(snapshot.result().score()).isEqualTo(50);
            });
  }

  @Test
  public void shouldRetryAndCompleteAfterRuleRejects() {
    // First attempt: bad result rejected by rule. Retry: good result accepted.
    agentModel
        .whenMessage(msg -> msg.contains("Do something"))
        .reply(completeTask(new TestTasks.TestResult("low quality", 3)));

    agentModel
        .whenMessage(msg -> msg.contains("Reminder"))
        .reply(completeTask(new TestTasks.TestResult("improved result", 50)));

    var taskId =
        componentClient
            .forAutonomousAgent(ValidatedTaskAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.VALIDATED_TASK.instructions("Do something."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.VALIDATED_TASK);
              assertThat(snapshot.status()).isEqualTo(TaskStatus.COMPLETED);
              assertThat(snapshot.result().value()).isEqualTo("improved result");
              assertThat(snapshot.result().score()).isEqualTo(50);
            });
  }

  @Test
  public void shouldFailAfterRepeatedRuleRejections() {
    // Agent always returns the same bad result — rule rejects every attempt
    // until maxIterationsPerTask is exhausted and the runtime fails the task
    agentModel.fixedResponse(completeTask(new TestTasks.TestResult("low quality", 3)));

    var taskId =
        componentClient
            .forAutonomousAgent(ValidatedTaskAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.VALIDATED_TASK.instructions("Do something poorly."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.VALIDATED_TASK);
              assertThat(snapshot.status()).isEqualTo(TaskStatus.FAILED);
              assertThat(snapshot.failureReason()).contains("Max iterations");
            });
  }
}
