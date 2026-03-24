/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.agent.task;

import static akka.Done.done;
import static org.assertj.core.api.Assertions.assertThat;

import akka.Done;
import akka.javasdk.agent.task.BacklogEntity;
import akka.javasdk.agent.task.BacklogEvent;
import akka.javasdk.agent.task.BacklogState;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

public class BacklogEntityTest {

  @Test
  public void shouldCreate() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    EventSourcedResult<Done> result = testKit.method(BacklogEntity::create).invoke("development");
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(BacklogEvent.BacklogCreated.class);
    assertThat(testKit.getState().name()).isEqualTo("development");
    assertThat(testKit.getState().isCreated()).isTrue();
  }

  @Test
  public void shouldCreateIdempotently() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::create).invoke("development");
    EventSourcedResult<Done> result = testKit.method(BacklogEntity::create).invoke("development");
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.getAllEvents()).isEmpty();
  }

  @Test
  public void shouldAddTask() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    EventSourcedResult<Done> result = testKit.method(BacklogEntity::addTask).invoke("task-1");
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(BacklogEvent.TaskAdded.class);
    assertThat(testKit.getState().containsTask("task-1")).isTrue();
    assertThat(testKit.getState().isClaimed("task-1")).isFalse();
  }

  @Test
  public void shouldAddTaskIdempotently() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    EventSourcedResult<Done> result = testKit.method(BacklogEntity::addTask).invoke("task-1");
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.getAllEvents()).isEmpty();
  }

  @Test
  public void shouldClaimTask() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");

    EventSourcedResult<Done> result =
        testKit
            .method(BacklogEntity::claim)
            .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-1"));
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(BacklogEvent.TaskClaimed.class);
    assertThat(testKit.getState().isClaimed("task-1")).isTrue();
    assertThat(testKit.getState().claimedBy("task-1")).hasValue("agent-1");
  }

  @Test
  public void shouldRejectClaimForNonExistentTask() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    EventSourcedResult<Done> result =
        testKit
            .method(BacklogEntity::claim)
            .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-1"));
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void shouldRejectClaimForAlreadyClaimedTask() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit
        .method(BacklogEntity::claim)
        .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-1"));

    EventSourcedResult<Done> result =
        testKit
            .method(BacklogEntity::claim)
            .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-2"));
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void shouldReleaseClaimedTask() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit
        .method(BacklogEntity::claim)
        .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-1"));

    EventSourcedResult<Done> result = testKit.method(BacklogEntity::release).invoke("task-1");
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(BacklogEvent.TaskReleased.class);
    assertThat(testKit.getState().isClaimed("task-1")).isFalse();
  }

  @Test
  public void shouldReleaseUnclaimedTaskIdempotently() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");

    EventSourcedResult<Done> result = testKit.method(BacklogEntity::release).invoke("task-1");
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.getAllEvents()).isEmpty();
  }

  @Test
  public void shouldTransferTask() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit
        .method(BacklogEntity::claim)
        .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-1"));

    EventSourcedResult<Done> result =
        testKit
            .method(BacklogEntity::transfer)
            .invoke(new BacklogEntity.TransferRequest("task-1", "agent-2"));
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(BacklogEvent.TaskTransferred.class);
    assertThat(testKit.getState().isClaimed("task-1")).isTrue();
    assertThat(testKit.getState().claimedBy("task-1")).hasValue("agent-2");
  }

  @Test
  public void shouldCancelUnclaimed() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit.method(BacklogEntity::addTask).invoke("task-2");
    testKit.method(BacklogEntity::addTask).invoke("task-3");
    testKit
        .method(BacklogEntity::claim)
        .invoke(new BacklogEntity.ClaimRequest("task-2", "agent-1"));

    EventSourcedResult<Done> result = testKit.method(BacklogEntity::cancelUnclaimed).invoke();
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(BacklogEvent.UnclaimedCancelled.class);

    assertThat(testKit.getState().containsTask("task-1")).isFalse();
    assertThat(testKit.getState().containsTask("task-2")).isTrue();
    assertThat(testKit.getState().containsTask("task-3")).isFalse();
  }

  @Test
  public void shouldCancelUnclaimedIdempotently() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    EventSourcedResult<Done> result = testKit.method(BacklogEntity::cancelUnclaimed).invoke();
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.getAllEvents()).isEmpty();
  }

  @Test
  public void shouldClose() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::create).invoke("development");

    EventSourcedResult<Done> result = testKit.method(BacklogEntity::close).invoke();
    assertThat(result.getReply()).isEqualTo(done());
    result.getNextEventOfType(BacklogEvent.BacklogClosed.class);
    assertThat(testKit.getState().closed()).isTrue();
  }

  @Test
  public void shouldCloseIdempotently() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::close).invoke();

    EventSourcedResult<Done> result = testKit.method(BacklogEntity::close).invoke();
    assertThat(result.getReply()).isEqualTo(done());
    assertThat(result.getAllEvents()).isEmpty();
  }

  @Test
  public void shouldRejectCreateWhenClosed() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::close).invoke();

    EventSourcedResult<Done> result = testKit.method(BacklogEntity::create).invoke("development");
    assertThat(result.getError()).isEqualTo("Backlog is closed");
  }

  @Test
  public void shouldRejectAddTaskWhenClosed() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::close).invoke();

    EventSourcedResult<Done> result = testKit.method(BacklogEntity::addTask).invoke("task-1");
    assertThat(result.getError()).isEqualTo("Backlog is closed");
  }

  @Test
  public void shouldRejectClaimWhenClosed() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit.method(BacklogEntity::close).invoke();

    EventSourcedResult<Done> result =
        testKit
            .method(BacklogEntity::claim)
            .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-1"));
    assertThat(result.getError()).isEqualTo("Backlog is closed");
  }

  @Test
  public void shouldRejectReleaseWhenClosed() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit
        .method(BacklogEntity::claim)
        .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-1"));
    testKit.method(BacklogEntity::close).invoke();

    EventSourcedResult<Done> result = testKit.method(BacklogEntity::release).invoke("task-1");
    assertThat(result.getError()).isEqualTo("Backlog is closed");
  }

  @Test
  public void shouldRejectTransferWhenClosed() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit
        .method(BacklogEntity::claim)
        .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-1"));
    testKit.method(BacklogEntity::close).invoke();

    EventSourcedResult<Done> result =
        testKit
            .method(BacklogEntity::transfer)
            .invoke(new BacklogEntity.TransferRequest("task-1", "agent-2"));
    assertThat(result.getError()).isEqualTo("Backlog is closed");
  }

  @Test
  public void shouldRejectCancelUnclaimedWhenClosed() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit.method(BacklogEntity::close).invoke();

    EventSourcedResult<Done> result = testKit.method(BacklogEntity::cancelUnclaimed).invoke();
    assertThat(result.getError()).isEqualTo("Backlog is closed");
  }

  @Test
  public void shouldAllowGetStateWhenClosed() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit.method(BacklogEntity::close).invoke();

    EventSourcedResult<BacklogState> result = testKit.method(BacklogEntity::getState).invoke();
    assertThat(result.getReply().closed()).isTrue();
    assertThat(result.getReply().containsTask("task-1")).isTrue();
  }

  @Test
  public void shouldGetState() {
    var testKit = EventSourcedTestKit.of(BacklogEntity::new);
    testKit.method(BacklogEntity::addTask).invoke("task-1");
    testKit.method(BacklogEntity::addTask).invoke("task-2");
    testKit
        .method(BacklogEntity::claim)
        .invoke(new BacklogEntity.ClaimRequest("task-1", "agent-1"));

    EventSourcedResult<BacklogState> result = testKit.method(BacklogEntity::getState).invoke();
    var state = result.getReply();
    assertThat(state.entries()).hasSize(2);
    assertThat(state.claimedTaskIds()).hasSize(1);
    assertThat(state.unclaimedTaskIds()).hasSize(1);
  }
}
