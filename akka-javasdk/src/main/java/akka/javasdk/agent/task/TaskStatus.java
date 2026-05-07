/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * The lifecycle status of a {@link Task}.
 *
 * <p>{@code PENDING}, {@code ASSIGNED}, {@code IN_PROGRESS}, and {@code RESULT_REJECTED} are
 * non-terminal. {@code COMPLETED}, {@code FAILED}, and {@code CANCELLED} are terminal.
 */
public enum TaskStatus {
  /** Created but not yet assigned to an agent. */
  PENDING,
  /** Assigned to an agent but not yet started. */
  ASSIGNED,
  /** An agent is actively working on it. */
  IN_PROGRESS,
  /** A {@link TaskRule} rejected the result. The agent retries on the next iteration. */
  RESULT_REJECTED,
  /** Finished successfully with a typed result. */
  COMPLETED,
  /**
   * Failed during execution, either by the model's decision or because the iteration limit was
   * reached.
   */
  FAILED,
  /** Terminated before execution began, for example by a dependency failure. */
  CANCELLED
}
