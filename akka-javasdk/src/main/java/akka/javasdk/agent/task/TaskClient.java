/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.Done;
import akka.javasdk.client.ComponentStreamMethodRef;
import java.util.List;

/**
 * Client for interacting with a task instance. Returned by {@link
 * akka.javasdk.client.ComponentClient#forTask(TaskRef)}.
 *
 * <p>Use {@link #get()} for a single-fetch snapshot of the task's state and typed result. Use
 * {@link #approve()}, {@link #reject(String)}, and {@link #notifications()} for actions and
 * streaming.
 *
 * @param <R> The result type of the task.
 */
public interface TaskClient<R> {

  /**
   * Create the task. The description and result type come from the task definition used to create
   * the {@link TaskRef}.
   */
  Done create();

  /**
   * Create the task with per-instance instructions.
   *
   * @param instructions context-specific instructions for this task instance
   */
  Done create(String instructions);

  /**
   * Create the task with per-instance instructions and dependencies on other tasks.
   *
   * @param instructions context-specific instructions for this task instance
   * @param dependencyTaskIds IDs of tasks that must complete before this one starts
   */
  Done create(String instructions, List<String> dependencyTaskIds);

  /**
   * Fetch the current state of the task as a typed snapshot. A single entity call — the result is
   * deserialized to {@code R}.
   */
  TaskSnapshot<R> get();

  /**
   * Approve a task that is awaiting approval (status {@link TaskStatus#AWAITING_APPROVAL}). The
   * task completes with the result that was pending approval.
   */
  Done approve();

  /**
   * Reject a task that is awaiting approval (status {@link TaskStatus#AWAITING_APPROVAL}). The task
   * fails with the given reason.
   *
   * @param reason why the approval was rejected
   */
  Done reject(String reason);

  /**
   * Subscribe to real-time notifications for this task's lifecycle events. Notifications follow the
   * task — they are published regardless of which agent is processing it, surviving handoffs.
   */
  ComponentStreamMethodRef<TaskNotification> notifications();
}
