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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
    return createRequest(name, null);
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
    assertThat(testKit.getState().assignee()).contains("agent-1");
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
    var completedEvent = result.getNextEventOfType(TaskEvent.TaskCompleted.class);
    assertThat(completedEvent.name()).isEqualTo("research");
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(testKit.getState().result()).contains("{\"summary\":\"done\"}");
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
    assertThat(testKit.getState().failureReason()).contains("something broke");
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
    assertThat(testKit.getState().assignee()).contains("agent-2");
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
    assertThat(completed.taskName()).isEqualTo("research");
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
    assertThat(failed.taskName()).isEqualTo("research");
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
    assertThat(cancelled.taskName()).isEqualTo("research");
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
    assertThat(testKit.getState().result()).contains("{\"approvedBy\":\"editor\"}");
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
    assertThat(testKit.getState().failureReason()).contains("rejected by editor");
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
  public void shouldRejectTask() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();
    publishedNotifications.clear();

    EventSourcedResult<Done> result =
        testKit
            .method(TaskEntity::rejectResult)
            .invoke(new TaskEntity.RejectResultRequest("com.example.MyRule", "result too short"));
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(TaskEvent.TaskResultRejected.class);
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.RESULT_REJECTED);
    assertThat(testKit.getState().failureReason()).contains("result too short");

    var rejected = (TaskNotification.ResultRejected) publishedNotifications.poll();
    assertThat(rejected.taskName()).isEqualTo("research");
    assertThat(rejected.ruleClassName()).isEqualTo("com.example.MyRule");
    assertThat(rejected.reason()).isEqualTo("result too short");
    assertThat(publishedNotifications).isEmpty();
  }

  @Test
  public void shouldCompleteAfterRejection() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();
    testKit
        .method(TaskEntity::rejectResult)
        .invoke(new TaskEntity.RejectResultRequest("com.example.MyRule", "result too short"));
    publishedNotifications.clear();

    EventSourcedResult<Done> result =
        testKit.method(TaskEntity::complete).invoke("{\"summary\":\"a longer result\"}");
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.COMPLETED);
  }

  @Test
  public void shouldTrackMultipleRejections() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();

    testKit
        .method(TaskEntity::rejectResult)
        .invoke(new TaskEntity.RejectResultRequest("Rule1", "first rejection"));
    testKit
        .method(TaskEntity::rejectResult)
        .invoke(new TaskEntity.RejectResultRequest("Rule2", "second rejection"));

    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.RESULT_REJECTED);
  }

  @Test
  public void shouldFailAfterRejection() {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    testKit.method(TaskEntity::start).invoke();
    testKit
        .method(TaskEntity::rejectResult)
        .invoke(new TaskEntity.RejectResultRequest("com.example.MyRule", "bad result"));

    EventSourcedResult<Done> result = testKit.method(TaskEntity::fail).invoke("giving up");
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(testKit.getState().status()).isEqualTo(TaskStatus.FAILED);
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

  // --- a task that does not exist ---

  private void assertNotFound(EventSourcedResult<?> result) {
    assertThat(result.isError()).isTrue();
    assertThat(result.getError()).isEqualTo("Task does not exist");
    assertThat(result.didPersistEvents()).isFalse();
  }

  @Test
  public void cancelOfTaskThatDoesNotExistStoresNothing() {
    var testKit = createTestKit();
    assertNotFound(testKit.method(TaskEntity::cancel).invoke("no longer needed"));
    assertThat(publishedNotifications).isEmpty();
  }

  @Test
  public void completeOfTaskThatDoesNotExistStoresNothing() {
    var testKit = createTestKit();
    assertNotFound(testKit.method(TaskEntity::complete).invoke("{\"summary\":\"done\"}"));
  }

  @Test
  public void failOfTaskThatDoesNotExistStoresNothing() {
    var testKit = createTestKit();
    assertNotFound(testKit.method(TaskEntity::fail).invoke("giving up"));
  }

  @Test
  public void assignOfTaskThatDoesNotExistStoresNothing() {
    var testKit = createTestKit();
    assertNotFound(testKit.method(TaskEntity::assign).invoke("agent-1"));
  }

  @Test
  public void startOfTaskThatDoesNotExistStoresNothing() {
    var testKit = createTestKit();
    assertNotFound(testKit.method(TaskEntity::start).invoke());
  }

  @Test
  public void rejectResultOfTaskThatDoesNotExistStoresNothing() {
    var testKit = createTestKit();
    assertNotFound(
        testKit
            .method(TaskEntity::rejectResult)
            .invoke(new TaskEntity.RejectResultRequest("com.example.MyRule", "bad result")));
  }

  @Test
  public void reassignOfTaskThatDoesNotExistStoresNothing() {
    var testKit = createTestKit();
    assertNotFound(
        testKit
            .method(TaskEntity::reassign)
            .invoke(new TaskEntity.ReassignRequest("agent-2", "handover")));
  }

  @Test
  public void getStateOfTaskThatDoesNotExistStoresNothing() {
    var testKit = createTestKit();
    assertNotFound(testKit.method(TaskEntity::getState).invoke());
  }

  // --- delete ---

  /** A fresh test kit with a task in the given status. */
  private EventSourcedTestKit<TaskState, TaskEvent, TaskEntity> testKitWithTaskIn(
      TaskStatus status) {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research"));
    switch (status) {
      case PENDING -> {}
      case ASSIGNED -> testKit.method(TaskEntity::assign).invoke("agent-1");
      case IN_PROGRESS -> {
        testKit.method(TaskEntity::assign).invoke("agent-1");
        testKit.method(TaskEntity::start).invoke();
      }
      case RESULT_REJECTED -> {
        testKit.method(TaskEntity::assign).invoke("agent-1");
        testKit.method(TaskEntity::start).invoke();
        testKit
            .method(TaskEntity::rejectResult)
            .invoke(new TaskEntity.RejectResultRequest("com.example.MyRule", "bad result"));
      }
      case COMPLETED -> {
        testKit.method(TaskEntity::assign).invoke("agent-1");
        testKit.method(TaskEntity::complete).invoke("{\"summary\":\"done\"}");
      }
      case FAILED -> {
        testKit.method(TaskEntity::assign).invoke("agent-1");
        testKit.method(TaskEntity::fail).invoke("giving up");
      }
      case CANCELLED -> testKit.method(TaskEntity::cancel).invoke("no longer needed");
    }
    assertThat(testKit.getState().status()).isEqualTo(status);
    return testKit;
  }

  @Test
  public void deleteTaskThatEnded() {
    for (var status : List.of(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED)) {
      var testKit = testKitWithTaskIn(status);

      EventSourcedResult<Done> result = testKit.method(TaskEntity::delete).invoke();

      assertThat(result.getReply()).as(status.name()).isEqualTo(done());
      var deleted = result.getNextEventOfType(TaskEvent.TaskDeleted.class);
      assertThat(deleted.taskId()).isEqualTo(testKit.getState().taskId());
      assertThat(deleted.name()).isEqualTo("research");
      assertThat(testKit.isDeleted()).as(status.name()).isTrue();
    }
  }

  @Test
  public void deleteOfTaskThatHasNotEndedFails() {
    for (var status :
        List.of(
            TaskStatus.PENDING,
            TaskStatus.ASSIGNED,
            TaskStatus.IN_PROGRESS,
            TaskStatus.RESULT_REJECTED)) {
      var testKit = testKitWithTaskIn(status);
      var stateBefore = testKit.getState();

      EventSourcedResult<Done> result = testKit.method(TaskEntity::delete).invoke();

      assertThat(result.isError()).as(status.name()).isTrue();
      assertThat(result.getError())
          .isEqualTo("Task can only be deleted when COMPLETED, FAILED or CANCELLED");
      assertThat(result.didPersistEvents()).isFalse();
      assertThat(testKit.getState()).isEqualTo(stateBefore);
      assertThat(testKit.isDeleted()).isFalse();
    }
  }

  @Test
  public void deleteAgainOrOfTaskThatDoesNotExistStoresNothing() {
    var testKit = testKitWithTaskIn(TaskStatus.COMPLETED);
    testKit.method(TaskEntity::delete).invoke();

    EventSourcedResult<Done> again = testKit.method(TaskEntity::delete).invoke();
    assertThat(again.getReply()).isEqualTo(done());
    assertThat(again.didPersistEvents()).isFalse();

    var neverCreated = createTestKit();
    EventSourcedResult<Done> result = neverCreated.method(TaskEntity::delete).invoke();
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.didPersistEvents()).isFalse();
    assertThat(neverCreated.isDeleted()).isFalse();
  }

  @Test
  public void deletedTaskIsNotFound() {
    var testKit = testKitWithTaskIn(TaskStatus.COMPLETED);
    testKit.method(TaskEntity::delete).invoke();
    publishedNotifications.clear();

    assertNotFound(testKit.method(TaskEntity::getState).invoke());
    assertNotFound(testKit.method(TaskEntity::assign).invoke("agent-2"));
    assertNotFound(testKit.method(TaskEntity::start).invoke());
    assertNotFound(testKit.method(TaskEntity::complete).invoke("{\"summary\":\"again\"}"));
    assertNotFound(
        testKit
            .method(TaskEntity::rejectResult)
            .invoke(new TaskEntity.RejectResultRequest("com.example.MyRule", "bad result")));
    assertNotFound(testKit.method(TaskEntity::fail).invoke("giving up"));
    assertNotFound(testKit.method(TaskEntity::cancel).invoke("no longer needed"));
    assertNotFound(
        testKit
            .method(TaskEntity::reassign)
            .invoke(new TaskEntity.ReassignRequest("agent-2", "handover")));

    EventSourcedResult<Done> create =
        testKit.method(TaskEntity::create).invoke(createRequest("research"));
    assertThat(create.isError()).isTrue();
    assertThat(create.getError()).isEqualTo("Task already exists");
    assertThat(create.didPersistEvents()).isFalse();

    assertThat(publishedNotifications).isEmpty();
  }

  // --- expiry ---

  private static final Duration ONE_HOUR = Duration.ofHours(1);

  private TaskEntity.CreateRequest createRequest(String name, Duration expireAfter) {
    return new TaskEntity.CreateRequest(
        name,
        "description",
        "instructions",
        "String",
        List.of(),
        List.of(),
        List.of(),
        expireAfter);
  }

  private EventSourcedTestKit<TaskState, TaskEvent, TaskEntity> assignedTaskWithExpiry(
      Duration expireAfter) {
    var testKit = createTestKit();
    testKit.method(TaskEntity::create).invoke(createRequest("research", expireAfter));
    testKit.method(TaskEntity::assign).invoke("agent-1");
    return testKit;
  }

  private void assertPersistedWithoutExpiry(EventSourcedResult<?> result) {
    assertThat(result.didPersistEvents()).isTrue();
    assertThat(result.getExpireAfter()).isEmpty();
  }

  @Test
  public void taskExpiresAfterItCompletes() {
    var testKit = assignedTaskWithExpiry(ONE_HOUR);
    EventSourcedResult<Done> result =
        testKit.method(TaskEntity::complete).invoke("{\"summary\":\"done\"}");
    result.getNextEventOfType(TaskEvent.TaskCompleted.class);
    assertThat(result.getExpireAfter()).contains(ONE_HOUR);
  }

  @Test
  public void taskExpiresAfterItFails() {
    var testKit = assignedTaskWithExpiry(ONE_HOUR);
    EventSourcedResult<Done> result = testKit.method(TaskEntity::fail).invoke("giving up");
    result.getNextEventOfType(TaskEvent.TaskFailed.class);
    assertThat(result.getExpireAfter()).contains(ONE_HOUR);
  }

  @Test
  public void taskExpiresAfterItIsCancelled() {
    var testKit = assignedTaskWithExpiry(ONE_HOUR);
    EventSourcedResult<Done> result = testKit.method(TaskEntity::cancel).invoke("no longer needed");
    result.getNextEventOfType(TaskEvent.TaskCancelled.class);
    assertThat(result.getExpireAfter()).contains(ONE_HOUR);
  }

  @Test
  public void noExpiryBeforeTaskEnds() {
    var testKit = createTestKit();

    EventSourcedResult<Done> create =
        testKit.method(TaskEntity::create).invoke(createRequest("research", ONE_HOUR));
    assertThat(create.getNextEventOfType(TaskEvent.TaskCreated.class).expireAfter())
        .isEqualTo(ONE_HOUR);
    assertThat(testKit.getState().expireAfter()).contains(ONE_HOUR);
    assertPersistedWithoutExpiry(create);

    assertPersistedWithoutExpiry(testKit.method(TaskEntity::assign).invoke("agent-1"));
    assertPersistedWithoutExpiry(testKit.method(TaskEntity::start).invoke());
    assertPersistedWithoutExpiry(
        testKit
            .method(TaskEntity::reassign)
            .invoke(new TaskEntity.ReassignRequest("agent-2", "handover")));
    assertPersistedWithoutExpiry(
        testKit
            .method(TaskEntity::rejectResult)
            .invoke(new TaskEntity.RejectResultRequest("com.example.MyRule", "bad result")));
  }

  @Test
  public void taskWithoutExpiryDoesNotExpire() {
    assertPersistedWithoutExpiry(
        assignedTaskWithExpiry(null).method(TaskEntity::complete).invoke("{\"summary\":\"done\"}"));
    assertPersistedWithoutExpiry(
        assignedTaskWithExpiry(null).method(TaskEntity::fail).invoke("giving up"));
    assertPersistedWithoutExpiry(
        assignedTaskWithExpiry(null).method(TaskEntity::cancel).invoke("no longer needed"));
  }

  @Test
  public void idempotentEndPersistsNothing() {
    var testKit = assignedTaskWithExpiry(ONE_HOUR);
    testKit.method(TaskEntity::complete).invoke("{\"summary\":\"done\"}");

    EventSourcedResult<Done> again =
        testKit.method(TaskEntity::complete).invoke("{\"other\":\"result\"}");
    assertThat(again.getReply()).isEqualTo(done());
    assertThat(again.didPersistEvents()).isFalse();
    assertThat(again.getExpireAfter()).isEmpty();
  }

  @Test
  public void createRejectsDurationThatIsNotPositive() {
    for (var expireAfter : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
      var testKit = createTestKit();
      EventSourcedResult<Done> result =
          testKit.method(TaskEntity::create).invoke(createRequest("research", expireAfter));
      assertThat(result.isError()).as(expireAfter.toString()).isTrue();
      assertThat(result.didPersistEvents()).isFalse();
    }
  }

  @Test
  public void createRejectsDurationThatEndsAfterTheYear9999() {
    var testKit = createTestKit();
    EventSourcedResult<Done> result =
        testKit
            .method(TaskEntity::create)
            .invoke(createRequest("research", ChronoUnit.FOREVER.getDuration()));
    assertThat(result.isError()).isTrue();
    assertThat(result.getError()).contains("before the year 10000");
    assertThat(result.didPersistEvents()).isFalse();
  }
}
