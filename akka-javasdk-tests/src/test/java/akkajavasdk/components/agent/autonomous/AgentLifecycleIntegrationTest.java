/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.failTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Integration tests for agent state management, pause/resume, and failure notifications. */
public class AgentLifecycleIntegrationTest extends TestKitSupport {

  private final TestModelProvider testAgentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(TestAutonomousAgent.class, testAgentModel);
  }

  @AfterEach
  public void afterEach() {
    testAgentModel.reset();
  }

  @Test
  public void shouldGetAgentState() {
    testAgentModel.fixedResponse(completeTask(new TestTasks.TestResult("done", 1)));

    var agentId = UUID.randomUUID().toString();
    var agentClient = componentClient.forAutonomousAgent(TestAutonomousAgent.class, agentId);

    var taskId =
        agentClient.runSingleTask(TestTasks.TEST_TASK.instructions("Do something simple."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
            });

    var state = agentClient.getState();
    assertThat(state.phase()).isEqualTo("PHASE_STOPPED");
    assertThat(state.paused()).isFalse();
    assertThat(state.totalTokenUsage()).isNotNull();
    assertThat(state.currentTask()).isEmpty();
    assertThat(state.pendingTaskIds()).isEmpty();
  }

  @Test
  public void shouldPauseAndResumeAgent() {
    testAgentModel.fixedResponse(completeTask(new TestTasks.TestResult("done after resume", 1)));

    var agentId = UUID.randomUUID().toString();
    var agentClient = componentClient.forAutonomousAgent(TestAutonomousAgent.class, agentId);

    agentClient.pause();

    var state = agentClient.getState();
    assertThat(state.paused()).isTrue();

    var taskId =
        componentClient
            .forTask(UUID.randomUUID().toString())
            .create(TestTasks.TEST_TASK.instructions("Do something."));
    agentClient.assignTasks(taskId);

    agentClient.resume();

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

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.status().name()).isEqualTo("FAILED");
            });

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
  public void shouldReceiveLifecycleNotifications() {
    testAgentModel.fixedResponse(
        new AiResponse(
            "",
            List.of(completeTask(new TestTasks.TestResult("done", 1))),
            Optional.of(new Agent.TokenUsage(150, 42))));

    var agentId = UUID.randomUUID().toString();
    var agentClient = componentClient.forAutonomousAgent(TestAutonomousAgent.class, agentId);

    var notifications = new ArrayList<Notification>();
    agentClient.notificationStream().runForeach(notifications::add, testKit.getMaterializer());

    var taskId =
        agentClient.runSingleTask(TestTasks.TEST_TASK.instructions("Do something simple."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              assertThat(snapshot.result()).isNotNull();
            });

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

              var iterationCompleted =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.IterationCompleted)
                      .map(n -> (Notification.IterationCompleted) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(iterationCompleted.tokenUsage().inputTokens()).isEqualTo(150);
              assertThat(iterationCompleted.tokenUsage().outputTokens()).isEqualTo(42);

              var taskAssigned =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.TaskAssigned)
                      .map(n -> (Notification.TaskAssigned) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(taskAssigned.taskId()).isEqualTo(taskId);

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

              var stopped =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.Stopped)
                      .map(n -> (Notification.Stopped) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(stopped.reason()).isNotBlank();

              // TaskAssigned should fire before TaskStarted
              assertThat(notifications.indexOf(taskAssigned))
                  .isLessThan(notifications.indexOf(taskStarted));
            });
  }

  @Test
  public void shouldReceivePauseAndResumeNotifications() {
    testAgentModel.fixedResponse(completeTask(new TestTasks.TestResult("done after resume", 1)));

    var agentId = UUID.randomUUID().toString();
    var agentClient = componentClient.forAutonomousAgent(TestAutonomousAgent.class, agentId);

    var notifications = new ArrayList<Notification>();
    agentClient.notificationStream().runForeach(notifications::add, testKit.getMaterializer());

    agentClient.pause();

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var paused =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.Paused)
                      .map(n -> (Notification.Paused) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(paused.reason()).isNotBlank();
            });

    // Assign a task while paused, then resume so the agent processes it.
    var taskId =
        componentClient
            .forTask(UUID.randomUUID().toString())
            .create(TestTasks.TEST_TASK.instructions("Do something."));
    agentClient.assignTasks(taskId);

    agentClient.resume();

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var resumed =
                  notifications.stream()
                      .filter(n -> n instanceof Notification.Resumed)
                      .map(n -> (Notification.Resumed) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(resumed.reason()).isNotBlank();
            });
  }
}
