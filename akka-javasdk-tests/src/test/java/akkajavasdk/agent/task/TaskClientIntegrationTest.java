/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.agent.task;

import static akkajavasdk.components.agent.autonomous.TestTasks.STRING_TASK;
import static akkajavasdk.components.agent.autonomous.TestTasks.TEST_TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.agent.task.TaskException;
import akka.javasdk.agent.task.TaskStatus;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.agent.autonomous.TestTasks.TestResult;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

public class TaskClientIntegrationTest extends TestKitSupport {

  private String newTaskId() {
    return UUID.randomUUID().toString();
  }

  // --- create ---

  @Test
  public void shouldCreateTask() {
    var taskId = newTaskId();
    var created = componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));
    assertThat(created).isEqualTo(taskId);
  }

  // --- get ---

  @Test
  public void shouldGetTaskSnapshot() {
    var taskId = newTaskId();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));

    var snapshot = componentClient.forTask(taskId).get(TEST_TASK);
    assertThat(snapshot.status()).isEqualTo(TaskStatus.PENDING);
    assertThat(snapshot.result()).isNull();
  }

  // --- assign ---

  @Test
  public void shouldAssignTask() {
    var taskId = newTaskId();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));
    componentClient.forTask(taskId).assign("reviewer@example.com");

    var snapshot = componentClient.forTask(taskId).get(TEST_TASK);
    assertThat(snapshot.status()).isEqualTo(TaskStatus.ASSIGNED);
  }

  // --- complete ---

  @Test
  public void shouldCompleteWithTypedResult() {
    var taskId = newTaskId();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));
    componentClient.forTask(taskId).assign("reviewer@example.com");
    componentClient.forTask(taskId).complete(TEST_TASK, new TestResult("done", 100));

    var snapshot = componentClient.forTask(taskId).get(TEST_TASK);
    assertThat(snapshot.status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(snapshot.result()).isNotNull();
    assertThat(snapshot.result().value()).isEqualTo("done");
    assertThat(snapshot.result().score()).isEqualTo(100);
  }

  @Test
  public void shouldCompleteWithStringResult() {
    var taskId = newTaskId();
    componentClient.forTask(taskId).create(STRING_TASK.instructions("summarize"));
    componentClient.forTask(taskId).assign("worker");
    componentClient.forTask(taskId).complete(STRING_TASK, "the summary");

    var snapshot = componentClient.forTask(taskId).get(STRING_TASK);
    assertThat(snapshot.status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(snapshot.result()).isEqualTo("the summary");
  }

  @Test
  public void shouldRejectCompleteWhenPending() {
    var taskId = newTaskId();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));

    assertThatThrownBy(
            () -> componentClient.forTask(taskId).complete(TEST_TASK, new TestResult("done", 100)))
        .hasMessageContaining("Task can only be completed when ASSIGNED or IN_PROGRESS");
  }

  // --- fail ---

  @Test
  public void shouldFailAssignedTask() {
    var taskId = newTaskId();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));
    componentClient.forTask(taskId).assign("reviewer@example.com");
    componentClient.forTask(taskId).fail("rejected");

    var snapshot = componentClient.forTask(taskId).get(TEST_TASK);
    assertThat(snapshot.status()).isEqualTo(TaskStatus.FAILED);
    assertThat(snapshot.failureReason()).isEqualTo("rejected");
  }

  @Test
  public void shouldRejectFailWhenPending() {
    var taskId = newTaskId();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));

    assertThatThrownBy(() -> componentClient.forTask(taskId).fail("rejected"))
        .hasMessageContaining("Task can only be failed when ASSIGNED or IN_PROGRESS");
  }

  // --- result ---

  @Test
  public void shouldReturnResultForAlreadyCompletedTask() {
    var taskId = newTaskId();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));
    componentClient.forTask(taskId).assign("reviewer@example.com");
    componentClient.forTask(taskId).complete(TEST_TASK, new TestResult("already done", 99));

    var result = componentClient.forTask(taskId).result(TEST_TASK);
    assertThat(result.value()).isEqualTo("already done");
    assertThat(result.score()).isEqualTo(99);
  }

  @Test
  public void shouldThrowFailedExceptionForAlreadyFailedTask() {
    var taskId = newTaskId();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));
    componentClient.forTask(taskId).assign("reviewer@example.com");
    componentClient.forTask(taskId).fail("not good enough");

    assertThatThrownBy(() -> componentClient.forTask(taskId).result(TEST_TASK))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(TaskException.Failed.class)
        .satisfies(
            ex -> {
              var cause = (TaskException.Failed) ex.getCause();
              assertThat(cause.taskId()).isEqualTo(taskId);
              assertThat(cause.reason()).isEqualTo("not good enough");
            });
  }
}
