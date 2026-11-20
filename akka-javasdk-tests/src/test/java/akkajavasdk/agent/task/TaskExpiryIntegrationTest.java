/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.agent.task;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;
import static akkajavasdk.components.agent.autonomous.TestTasks.TEST_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.components.agent.autonomous.CoordinatorAgent;
import akkajavasdk.components.agent.autonomous.TestAutonomousAgent;
import akkajavasdk.components.agent.autonomous.TestTasks;
import akkajavasdk.components.agent.autonomous.WorkerAgent;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class TaskExpiryIntegrationTest extends TestKitSupport {

  private final TestModelProvider testAgentModel = new TestModelProvider();
  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider workerModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.task.expire-after = 1 hour")
        .withModelProvider(TestAutonomousAgent.class, testAgentModel)
        .withModelProvider(CoordinatorAgent.class, coordinatorModel)
        .withModelProvider(WorkerAgent.class, workerModel);
  }

  @AfterEach
  public void afterEach() {
    testAgentModel.reset();
    coordinatorModel.reset();
    workerModel.reset();
  }

  private Duration storedExpireAfter(String taskId) {
    return componentClient
        .forEventSourcedEntity(taskId)
        .method(TaskEntity::getState)
        .invoke()
        .expireAfter()
        .orElseThrow();
  }

  @Test
  public void shouldSetTheDefaultExpiryOnTaskWithoutOne() {
    var taskId = UUID.randomUUID().toString();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));

    assertThat(storedExpireAfter(taskId)).isEqualTo(Duration.ofHours(1));
  }

  @Test
  public void shouldKeepTheExpiryOfTheTask() {
    var taskId = UUID.randomUUID().toString();
    componentClient
        .forTask(taskId)
        .create(TEST_TASK.expireAfter(Duration.ofMinutes(10)).instructions("do something"));

    assertThat(storedExpireAfter(taskId)).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  public void shouldSetTheDefaultExpiryOnTaskOfRunSingleTask() {
    testAgentModel.fixedResponse(completeTask(new TestTasks.TestResult("done", 1)));

    var taskId =
        componentClient
            .forAutonomousAgent(TestAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TEST_TASK.instructions("do something"));

    assertThat(storedExpireAfter(taskId)).isEqualTo(Duration.ofHours(1));
  }

  @Test
  public void shouldSetTheDefaultExpiryOnTaskThatAnAgentDelegates() {
    coordinatorModel
        .whenMessage(msg -> msg.contains("quantum"))
        .reply(
            delegateTo(
                TestTasks.FINDINGS, WorkerAgent.class, "Research quantum computing fundamentals"));
    workerModel.fixedResponse(
        completeTask(new TestTasks.FindingsResult("Quantum Computing", "Qubits.")));
    coordinatorModel
        .whenMessage(msg -> msg.contains("Continue working"))
        .reply(completeTask(new TestTasks.ResearchResult("Quantum Computing", "Qubits.")));

    var coordinatorClient =
        componentClient.forAutonomousAgent(CoordinatorAgent.class, UUID.randomUUID().toString());
    var notifications = new CopyOnWriteArrayList<Notification>();
    coordinatorClient
        .notificationStream()
        .runForeach(notifications::add, testKit.getMaterializer());

    coordinatorClient.runSingleTask(TestTasks.RESEARCH.instructions("Research quantum computing"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var subtaskId =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.DelegationStarted)
                      .map(n -> (Notification.DelegationStarted) n)
                      .findFirst()
                      .orElseThrow()
                      .subtaskIds()
                      .getFirst();
              assertThat(storedExpireAfter(subtaskId)).isEqualTo(Duration.ofHours(1));
            });
  }
}
