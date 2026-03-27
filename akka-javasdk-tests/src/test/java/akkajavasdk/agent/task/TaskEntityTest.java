/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.agent.task;

import static akka.Done.done;
import static org.assertj.core.api.Assertions.assertThat;

import akka.Done;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.agent.task.TaskEvent;
import akka.javasdk.agent.task.TaskNotification;
import akka.javasdk.agent.task.TaskState;
import akka.javasdk.agent.task.TaskStatus;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;

public class TaskEntityTest {

  private final ConcurrentLinkedQueue<TaskNotification> publishedNotifications =
      new ConcurrentLinkedQueue<>();

  private final NotificationPublisher<TaskNotification> testPublisher =
      msg -> publishedNotifications.add(msg);

  private EventSourcedTestKit<TaskState, TaskEvent, TaskEntity> createTestKit() {
    return EventSourcedTestKit.of(ctx -> new TaskEntity(ctx, testPublisher));
  }

  private TaskEntity.CreateRequest createRequest(String name) {
    return new TaskEntity.CreateRequest(
        name, "description", "instructions", "String", List.of(), List.of());
  }

  @Test
  public void shouldCreateTask() {
    var testKit = createTestKit();
    EventSourcedResult<Done> result =
        testKit.method(TaskEntity::create).invoke(createRequest("research"));
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskCreated.class);
    assertThat(testKit.getState().name()).isEqualTo("research");
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.PENDING);
  }

  @Test
  public void shouldRejectDuplicateCreate() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    EventSourcedResult<Done> result =
        testKit.method(TaskEntity::create).invoke(createRequest("research"));
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void shouldAssignTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));

    EventSourcedResult<Done> result = testKit.method(TaskEntity::assign).invoke("agent-1");
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskAssigned.class);
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.ASSIGNED);
    assertThat(testKit.getState().assignee()).isEqualTo("agent-1");
  }

  @Test
  public void shouldRejectAssignWhenNotPending() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");

    EventSourcedResult<Done> result = testKit.method(TaskEntity::assign).invoke("agent-2");
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void shouldStartTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");

    EventSourcedResult<Done> result = testKit.method(TaskEntity::start).invoke();
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskStarted.class);
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  public void shouldCompleteTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();

    EventSourcedResult<Done> result =
        testKit.method(TaskEntity::complete).invoke("{\"summary\":\"done\"}");
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskCompleted.class);
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(testKit.getState().result()).isEqualTo("{\"summary\":\"done\"}");
  }

  @Test
  public void shouldCompleteIdempotentlyWhenTerminal() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();
    testKit.method(TaskEntity::complete).invoke("{\"summary\":\"done\"}");

    EventSourcedResult<Done> result =
        testKit.method(TaskEntity::complete).invoke("{\"other\":\"result\"}");
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.getAllEvents()).isEmpty();
  }

  @Test
  public void shouldFailTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();

    EventSourcedResult<Done> result = testKit.method(TaskEntity::fail).invoke("something broke");
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskFailed.class);
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.FAILED);
    assertThat(testKit.getState().failureReason()).isEqualTo("something broke");
  }

  @Test
  public void shouldCancelPendingTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));

    EventSourcedResult<Done> result = testKit.method(TaskEntity::cancel).invoke("no longer needed");
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskCancelled.class);
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.CANCELLED);
  }

  @Test
  public void shouldCancelAssignedTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");

    EventSourcedResult<Done> result = testKit.method(TaskEntity::cancel).invoke("no longer needed");
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.CANCELLED);
  }

  @Test
  public void shouldRejectCancelWhenInProgress() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();

    EventSourcedResult<Done> result = testKit.method(TaskEntity::cancel).invoke("no longer needed");
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void shouldReassignTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();

    EventSourcedResult<Done> result =
        testKit
            .method(TaskEntity::reassign)
            .invoke(new TaskEntity.ReassignRequest("agent-2", "needs different expertise"));
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskReassigned.class);
    assertThat(testKit.getState().assignee()).isEqualTo("agent-2");
    assertThat(testKit.getState().reassignmentContext())
        .containsExactly("needs different expertise");
  }

  @Test
  public void shouldGetState() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));

    EventSourcedResult<TaskState> result = testKit.method(TaskEntity::getState).invoke();
    assertThat(result.getReply().name()).isEqualTo("research");
    assertThat(result.getReply().status()).isEqualTo(TaskStatus.PENDING);
  }

  @Test
  public void shouldRejectGetStateForNonExistentTask() {
    var testKit = createTestKit();
    EventSourcedResult<TaskState> result = testKit.method(TaskEntity::getState).invoke();
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void shouldPublishNotificationOnComplete() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();
    publishedNotifications.clear();

    testKit.method(TaskEntity::complete).invoke("{\"summary\":\"done\"}");

    var completed = (TaskNotification.Completed) publishedNotifications.poll();
    assertThat(completed.result()).isEqualTo("{\"summary\":\"done\"}");
    assertThat(publishedNotifications).isEmpty();
  }

  @Test
  public void shouldPublishNotificationOnFail() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();
    publishedNotifications.clear();

    testKit.method(TaskEntity::fail).invoke("something broke");

    var failed = (TaskNotification.Failed) publishedNotifications.poll();
    assertThat(failed.reason()).isEqualTo("something broke");
    assertThat(publishedNotifications).isEmpty();
  }

  @Test
  public void shouldPublishNotificationOnCancel() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    publishedNotifications.clear();

    testKit.method(TaskEntity::cancel).invoke("no longer needed");

    var cancelled = (TaskNotification.Cancelled) publishedNotifications.poll();
    assertThat(cancelled.reason()).isEqualTo("no longer needed");
    assertThat(publishedNotifications).isEmpty();
  }

  @Test
  public void shouldCompleteAssignedTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("approval"));
    testKit.method(TaskEntity::assign).invoke("editor@example.com");
    publishedNotifications.clear();

    EventSourcedResult<Done> result =
        testKit.method(TaskEntity::complete).invoke("{\"approvedBy\":\"editor\"}");
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskCompleted.class);
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(testKit.getState().result()).isEqualTo("{\"approvedBy\":\"editor\"}");
    assertThat(publishedNotifications.poll()).isInstanceOf(TaskNotification.Completed.class);
    assertThat(publishedNotifications).isEmpty();
  }

  @Test
  public void shouldFailAssignedTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("approval"));
    testKit.method(TaskEntity::assign).invoke("editor@example.com");
    publishedNotifications.clear();

    EventSourcedResult<Done> result = testKit.method(TaskEntity::fail).invoke("rejected by editor");
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskFailed.class);
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.FAILED);
    assertThat(testKit.getState().failureReason()).isEqualTo("rejected by editor");
    assertThat(publishedNotifications.poll()).isInstanceOf(TaskNotification.Failed.class);
    assertThat(publishedNotifications).isEmpty();
  }

  @Test
  public void shouldRejectCompleteWhenPending() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));

    EventSourcedResult<Done> result =
        testKit.method(TaskEntity::complete).invoke("{\"summary\":\"done\"}");
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void shouldRejectFailWhenPending() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));

    EventSourcedResult<Done> result = testKit.method(TaskEntity::fail).invoke("something broke");
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void shouldNotPublishNotificationOnIdempotentComplete() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();
    testKit.method(TaskEntity::complete).invoke("{\"summary\":\"done\"}");
    publishedNotifications.clear();

    testKit.method(TaskEntity::complete).invoke("{\"other\":\"result\"}");

    assertThat(publishedNotifications).isEmpty();
  }
}
