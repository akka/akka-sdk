/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import java.time.Duration;
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
 * the polling continues after a restart. It deletes itself when the task ends or needs input, or
 * after one poll when it only checks for changes made before a push notification configuration was
 * registered.
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
   * @param intervalMillis the time between polls
   * @param once true to poll once, without waiting first
   * @param failures the number of polls that failed in a row
   */
  public record State(
      String subscriptionId,
      A2aRemoteRef remote,
      String taskId,
      long intervalMillis,
      boolean once,
      int failures) {
    State withFailures(int failures) {
      return new State(subscriptionId, remote, taskId, intervalMillis, once, failures);
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

  private StepEffect poll() {
    var poll = currentState();
    boolean continuePolling;
    try {
      continuePolling =
          remoteTasks
              .poll(poll.subscriptionId(), poll.remote(), poll.taskId())
              .toCompletableFuture()
              .get(POLL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (ExecutionException | TimeoutException e) {
      var failures = poll.failures() + 1;
      var cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
      if (poll.once() || failures >= MAX_FAILURES) {
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
    if (!continuePolling || poll.once()) return stepEffects().thenDelete();
    var updated = poll.withFailures(0);
    return stepEffects().updateState(updated).thenPause(pauseUntilNextPoll(updated));
  }

  private PauseSettings pauseUntilNextPoll(State poll) {
    return pauseSetting(Duration.ofMillis(poll.intervalMillis()))
        .timeoutHandler(A2aRemoteTaskPollWorkflow::pollNow);
  }
}
