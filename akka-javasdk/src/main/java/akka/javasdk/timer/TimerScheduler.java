/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.timer;

import akka.Done;
import akka.javasdk.DeferredCall;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

public interface TimerScheduler {

  /**
   * Schedule a single timer. Timers allow for scheduling calls in the future. For example, to
   * verify that some process have been completed or not.
   *
   * <p>Timers are persisted and are guaranteed to run at least once.
   *
   * <p>When a timer is triggered, the scheduled call is executed. If successfully executed, the
   * timer completes and is automatically removed. In case of a failure, the timer is rescheduled
   * with an exponentially increasing delay, starting at 3 seconds with a max delay of 30 seconds.
   * This process repeats until the call succeeds.
   *
   * <p>Each timer has a {@code name} and if a new timer with same {@code name} is registered the
   * previous is cancelled.
   *
   * @param name unique name for the timer
   * @param delay delay, starting from now, in which the timer should be triggered
   * @param deferredCall a call to component that will be executed when the timer is triggered
   */
  <I, O> void createSingleTimer(String name, Duration delay, DeferredCall<I, O> deferredCall);

  /**
   * Schedule a single timer. Timers allow for scheduling calls in the future. For example, to
   * verify that some process have been completed or not.
   *
   * <p>Timers are persisted and are guaranteed to run at least once.
   *
   * <p>When a timer is triggered, the scheduled call is executed. If successfully executed, the
   * timer completes and is automatically removed. In case of a failure, the timer is rescheduled
   * with a delay of 3 seconds. This process repeats until the call succeeds or the maxRetries limit
   * is reached.
   *
   * <p>Each timer has a {@code name} and if a new timer with same {@code name} is registered the
   * previous is cancelled.
   *
   * @param name unique name for the timer
   * @param delay delay, starting from now, in which the timer should be triggered
   * @param maxRetries Retry up to this many times before giving up
   * @param deferredCall a call to component that will be executed when the timer is triggered
   */
  <I, O> void createSingleTimer(
      String name, Duration delay, int maxRetries, DeferredCall<I, O> deferredCall);

  /**
   * Delete an existing timer. This completes successfully if no timer is registered for the passed
   * name.
   */
  void delete(String name);

  /**
   * Schedule a single timer. Timers allow for scheduling calls in the future. For example, to
   * verify that some process have been completed or not.
   *
   * <p>Timers are persisted and are guaranteed to run at least once.
   *
   * <p>When a timer is triggered, the scheduled call is executed. If successfully executed, the
   * timer completes and is automatically removed. In case of a failure, the timer is rescheduled
   * with an exponentially increasing delay, starting at 3 seconds with a max delay of 30 seconds.
   * This process repeats until the call succeeds.
   *
   * <p>Each timer has a {@code name} and if a new timer with same {@code name} is registered the
   * previous is cancelled.
   *
   * @param name unique name for the timer
   * @param delay delay, starting from now, in which the timer should be triggered
   * @param deferredCall a call to component that will be executed when the timer is triggered
   * @return A completion stage that is completed successfully or failed, once the timer has been
   *     scheduled
   */
  <I, O> CompletionStage<Done> createSingleTimerAsync(
      String name, Duration delay, DeferredCall<I, O> deferredCall);

  /**
   * Schedule a single timer. Timers allow for scheduling calls in the future. For example, to
   * verify that some process have been completed or not.
   *
   * <p>Timers are persisted and are guaranteed to run at least once.
   *
   * <p>When a timer is triggered, the scheduled call is executed. If successfully executed, the
   * timer completes and is automatically removed. In case of a failure, the timer is rescheduled
   * with a delay of 3 seconds. This process repeats until the call succeeds or the maxRetries limit
   * is reached.
   *
   * <p>Each timer has a {@code name} and if a new timer with same {@code name} is registered the
   * previous is cancelled.
   *
   * @param name unique name for the timer
   * @param delay delay, starting from now, in which the timer should be triggered
   * @param maxRetries Retry up to this many times before giving up
   * @param deferredCall a call to component that will be executed when the timer is triggered
   * @return A completion stage that is completed successfully or failed, once the timer has been
   *     scheduled
   */
  <I, O> CompletionStage<Done> createSingleTimerAsync(
      String name, Duration delay, int maxRetries, DeferredCall<I, O> deferredCall);

  /**
   * Delete an existing timer. This completes successfully if no timer is registered for the passed
   * name.
   */
  CompletionStage<Done> deleteAsync(String name);

  /**
   * @deprecated Use {@link TimerScheduler#createSingleTimerAsync(String, Duration, DeferredCall)}
   *     instead.
   */
  @Deprecated(since = "3.3.0", forRemoval = true)
  <I, O> CompletionStage<Done> startSingleTimer(
      String name, Duration delay, DeferredCall<I, O> deferredCall);

  /**
   * @deprecated Use {@link TimerScheduler#createSingleTimerAsync(String, Duration, int,
   *     DeferredCall)} instead.
   */
  @Deprecated(since = "3.3.0", forRemoval = true)
  <I, O> CompletionStage<Done> startSingleTimer(
      String name, Duration delay, int maxRetries, DeferredCall<I, O> deferredCall);

  /** @deprecated User {@link TimerScheduler#deleteAsync(String)} instead. */
  @Deprecated(since = "3.3.0", forRemoval = true)
  CompletionStage<Done> cancel(String name);
}
