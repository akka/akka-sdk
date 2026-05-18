/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.Done;
import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.workflow.Workflow;
import java.util.concurrent.CompletionStage;

/** Not for user extension */
@DoNotInherit
public interface WorkflowClient {

  /** Pass in a Workflow command handler method reference, e.g. {@code MyWorkflow::start} */
  <T, R> ComponentMethodRef<R> method(Function<T, Workflow.Effect<R>> methodRef);

  /** Pass in a Workflow command handler method reference, e.g. {@code MyWorkflow::start} */
  <T, A1, R> ComponentMethodRef1<A1, R> method(Function2<T, A1, Workflow.Effect<R>> methodRef);

  /**
   * Pass in a Workflow notification stream getter method reference, e.g. {@code
   * MyWorkflow::updates}
   */
  <T, R> ComponentStreamMethodRef<R> notificationStream(
      Function<T, NotificationPublisher.NotificationStream<R>> methodRef);

  /**
   * Terminate the workflow identified by this client's workflow id. Termination stops the workflow
   * immediately (including any in-flight step), cancels pause/step/workflow-timeout timers, and
   * moves the workflow into a terminal {@code Terminated} state from which no further transitions
   * are possible. The call is idempotent — terminating an already-finished workflow is a no-op.
   *
   * @param workflowClass the Workflow class, used to resolve the component id the runtime needs to
   *     route the termination command.
   */
  Done terminate(Class<? extends Workflow<?>> workflowClass);

  /** Async variant of {@link #terminate(Class)}. */
  CompletionStage<Done> terminateAsync(Class<? extends Workflow<?>> workflowClass);

  /**
   * Terminate the workflow identified by this client's workflow id, recording a free-form reason.
   * An empty {@code reason} is equivalent to {@link #terminate(Class)}.
   *
   * <p>The reason is short, human-readable text — same guidance as the paused-workflow reason. It
   * is persisted in the workflow's event journal and written to runtime logs at termination time,
   * so it MUST NOT contain secrets or PII.
   *
   * <p>On a workflow that is already in a terminal state, the call is a successful no-op and the
   * previously-recorded reason (if any) is not overwritten.
   *
   * @param workflowClass the Workflow class, used to resolve the component id.
   * @param reason free-form reason. Must not be {@code null}.
   */
  Done terminate(Class<? extends Workflow<?>> workflowClass, String reason);

  /** Async variant of {@link #terminate(Class, String)}. */
  CompletionStage<Done> terminateAsync(Class<? extends Workflow<?>> workflowClass, String reason);

  /**
   * Suspend the workflow identified by this client's workflow id. Suspension halts the workflow at
   * its current step boundary: any in-flight step is allowed to complete, pending pause/step timers
   * are cancelled, and the workflow moves into a {@code Suspended} state from which it can later be
   * brought back with {@link #resume(Class)}. The call is idempotent — suspending an
   * already-suspended workflow is a no-op.
   *
   * @param workflowClass the Workflow class, used to resolve the component id the runtime needs to
   *     route the suspend command.
   */
  Done suspend(Class<? extends Workflow<?>> workflowClass);

  /** Async variant of {@link #suspend(Class)}. */
  CompletionStage<Done> suspendAsync(Class<? extends Workflow<?>> workflowClass);

  /**
   * Suspend the workflow identified by this client's workflow id, recording a free-form reason. An
   * empty {@code reason} is equivalent to {@link #suspend(Class)}.
   *
   * <p>The reason is short, human-readable text — same guidance as the paused-workflow reason. It
   * is persisted in the workflow's event journal and written to runtime logs at suspend time, so it
   * MUST NOT contain secrets or PII.
   *
   * <p>On a workflow that is already suspended, the call is a successful no-op and the
   * previously-recorded reason (if any) is not overwritten.
   *
   * @param workflowClass the Workflow class, used to resolve the component id.
   * @param reason free-form reason. Must not be {@code null}.
   */
  Done suspend(Class<? extends Workflow<?>> workflowClass, String reason);

  /** Async variant of {@link #suspend(Class, String)}. */
  CompletionStage<Done> suspendAsync(Class<? extends Workflow<?>> workflowClass, String reason);

  /**
   * Resume the workflow identified by this client's workflow id. Resumption is only meaningful
   * against a workflow currently in the {@code Suspended} state — it transitions the workflow back
   * to the step that was pending at suspend time and lets execution continue. The call is
   * idempotent on a non-suspended workflow: it is a successful no-op.
   *
   * @param workflowClass the Workflow class, used to resolve the component id the runtime needs to
   *     route the resume command.
   */
  Done resume(Class<? extends Workflow<?>> workflowClass);

  /** Async variant of {@link #resume(Class)}. */
  CompletionStage<Done> resumeAsync(Class<? extends Workflow<?>> workflowClass);
}
