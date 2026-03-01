/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * Sealed supertype for task definitions. A task definition declares what kind of work an agent can
 * do — a description of the task and the expected result type.
 *
 * <p>The only implementation is {@link Task}. Future stages will add {@code TaskTemplate} for
 * parameterized instruction templates.
 *
 * @param <R> The result type produced when the task completes.
 */
public sealed interface TaskDef<R> permits Task {

  /** The description of this task — what kind of work it represents. */
  String description();

  /** The expected result type. */
  Class<R> resultType();

  /** Create a typed reference to a specific task instance by ID. */
  TaskRef<R> ref(String taskId);
}
