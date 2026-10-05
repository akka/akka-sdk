/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class AutonomousAgentGuardrailIntegrationTest extends TestKitSupport {

  private final TestModelProvider guardedModel = new TestModelProvider();
  private final TestModelProvider legacyGuardedModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(GuardedAutonomousAgent.class, guardedModel)
        .withModelProvider(LegacyGuardedAutonomousAgent.class, legacyGuardedModel);
  }

  @AfterEach
  public void afterEach() {
    guardedModel.reset();
    legacyGuardedModel.reset();
  }

  @Test
  public void shouldFailTaskWhenModelCallGuardrailDenies() {
    guardedModel.fixedResponse(completeTask("done"));

    var taskId =
        componentClient
            .forAutonomousAgent(GuardedAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.STRING_TASK.instructions("Say hello"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.STRING_TASK);
              assertThat(snapshot.status().name()).isEqualTo("FAILED");
            });
  }

  @Test
  public void shouldFailTaskWhenLegacyRequestGuardrailDenies() {
    legacyGuardedModel.fixedResponse(completeTask("done"));

    var taskId =
        componentClient
            .forAutonomousAgent(LegacyGuardedAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.STRING_TASK.instructions("Tell me about the forbidden topic"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.STRING_TASK);
              assertThat(snapshot.status().name()).isEqualTo("FAILED");
            });
  }

  @Test
  public void shouldCompleteTaskWhenLegacyRequestGuardrailAllows() {
    legacyGuardedModel.fixedResponse(completeTask("done"));

    var taskId =
        componentClient
            .forAutonomousAgent(LegacyGuardedAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.STRING_TASK.instructions("Tell me about the weather"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.STRING_TASK);
              assertThat(snapshot.result()).contains("done");
            });
  }
}
