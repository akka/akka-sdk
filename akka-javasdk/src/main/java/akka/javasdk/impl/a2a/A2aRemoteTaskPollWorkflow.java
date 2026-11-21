/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL API
 *
 * <p>Asks a remote agent for the state of one task with {@code GetTask} and delivers each change to
 * the subscription's workflow command. Between polls the workflow pauses for the poll interval, so
 * the polling continues after a restart. It deletes itself when the task ends or needs input.
 *
 * <p>When the remote agent sends push notifications, the workflow polls once, for changes made
 * before the push notification configuration was registered, and then waits for the deadline.
 *
 * <p>At the deadline, {@code follow-timeout} after the task was first followed, the following ends:
 * unless the subscription has received an update that ends the task, the workflow command receives
 * a status update whose {@code followTimedOut()} is true.
 */
@InternalApi
@Component(id = "akka-a2a-remote-task-poll")
public final class A2aRemoteTaskPollWorkflow extends Workflow<A2aRemoteTaskPollWorkflow.State> {

  private static final Logger log = LoggerFactory.getLogger(A2aRemoteTaskPollWorkflow.class);

  /** Polls that fail in a row before the workflow gives up. */
  static final int MAX_FAILURES = 20;

  static final Duration POLL_TIMEOUT = Duration.ofMinutes(2);

  /**
   * @param subscriptionId the id of the {@link A2aRemoteTaskSubscriptionEntity}
   * @param remote the remote agent
   * @param taskId the id of the remote task
   * @param contextId the context id of the remote task
   * @param intervalMillis the time between polls
   * @param once true to poll once, without waiting first, and then wait for the deadline
   * @param failures the number of polls that failed in a row
   * @param deadlineMillis when the following ends, in milliseconds since the epoch
   * @param followTimeoutMillis the follow timeout that set the deadline
   */
  public record State(
      String subscriptionId,
      A2aRemoteRef remote,
      String taskId,
      String contextId,
      long intervalMillis,
      boolean once,
      int failures,
      long deadlineMillis,
      long followTimeoutMillis) {
    State withFailures(int failures) {
      return new State(
          subscriptionId,
          remote,
          taskId,
          contextId,
          intervalMillis,
          once,
          failures,
          deadlineMillis,
          followTimeoutMillis);
    }
  }

  private final A2aRemoteTasks remoteTasks;

  public A2aRemoteTaskPollWorkflow(A2aRemoteTasks remoteTasks) {
    this.remoteTasks = remoteTasks;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder().defaultStepTimeout(POLL_TIMEOUT.plusSeconds(5)).build();
  }

  /** Starts the polling. A polling that has already started is left as it is. */
  public Effect<Done> start(State poll) {
    if (currentState() != null) return effects().reply(Done.getInstance());
    if (poll.once()) {
      return effects()
          .updateState(poll)
          .transitionTo(A2aRemoteTaskPollWorkflow::poll)
          .thenReply(Done.getInstance());
    }
    return effects()
        .updateState(poll)
        .pause(pauseUntilNextPoll(poll))
        .thenReply(Done.getInstance());
  }

  /** Called when the pause between two polls ends. */
  public Effect<Done> pollNow() {
    if (currentState() == null) return effects().error("No remote task to poll");
    return effects().transitionTo(A2aRemoteTaskPollWorkflow::poll).thenReply(Done.getInstance());
  }

  /** Called when the pause until the deadline ends. */
  public Effect<Done> deadlineReached() {
    if (currentState() == null) return effects().error("No remote task to follow");
    return effects()
        .transitionTo(A2aRemoteTaskPollWorkflow::endFollowing)
        .thenReply(Done.getInstance());
  }

  private StepEffect poll() {
    var poll = currentState();
    if (System.currentTimeMillis() >= poll.deadlineMillis()) return endFollowing();
    boolean continuePolling;
    try {
      continuePolling =
          await(remoteTasks.poll(poll.subscriptionId(), poll.remote(), poll.taskId()));
    } catch (ExecutionException | TimeoutException e) {
      var failures = poll.failures() + 1;
      var cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
      if (poll.once()) {
        log.debug("Polling remote A2A task [{}] failed", poll.taskId(), cause);
        return stepEffects().thenPause(pauseUntilDeadline(poll));
      }
      if (failures >= MAX_FAILURES) {
        log.warn(
            "Stopped following remote A2A task [{}] after {} failed polls",
            poll.taskId(),
            failures,
            cause);
        return stepEffects().thenDelete();
      }
      log.debug("Polling remote A2A task [{}] failed", poll.taskId(), cause);
      var updated = poll.withFailures(failures);
      return stepEffects().updateState(updated).thenPause(pauseUntilNextPoll(updated));
    }
    if (!continuePolling) return stepEffects().thenDelete();
    if (poll.once()) return stepEffects().thenPause(pauseUntilDeadline(poll));
    var updated = poll.withFailures(0);
    return stepEffects().updateState(updated).thenPause(pauseUntilNextPoll(updated));
  }

  private StepEffect endFollowing() {
    var poll = currentState();
    try {
      await(
          remoteTasks.followTimedOut(
              poll.subscriptionId(),
              poll.taskId(),
              poll.contextId(),
              Duration.ofMillis(poll.followTimeoutMillis())));
    } catch (ExecutionException | TimeoutException e) {
      var cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
      log.warn(
          "Following remote A2A task [{}] timed out, and the workflow could not be told",
          poll.taskId(),
          cause);
    }
    return stepEffects().thenDelete();
  }

  private static <T> T await(CompletionStage<T> stage) throws ExecutionException, TimeoutException {
    try {
      return stage.toCompletableFuture().get(POLL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static Duration untilDeadline(State poll) {
    return Duration.ofMillis(Math.max(1, poll.deadlineMillis() - System.currentTimeMillis()));
  }

  private PauseSettings pauseUntilNextPoll(State poll) {
    var interval = Duration.ofMillis(poll.intervalMillis());
    var untilDeadline = untilDeadline(poll);
    return pauseSetting(interval.compareTo(untilDeadline) <= 0 ? interval : untilDeadline)
        .timeoutHandler(A2aRemoteTaskPollWorkflow::pollNow);
  }

  private PauseSettings pauseUntilDeadline(State poll) {
    return pauseSetting(untilDeadline(poll))
        .timeoutHandler(A2aRemoteTaskPollWorkflow::deadlineReached);
  }
}
