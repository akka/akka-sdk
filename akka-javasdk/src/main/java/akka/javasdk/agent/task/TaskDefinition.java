/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * A task definition declares what kind of work an agent can do — a description of the task and the
 * expected result type.
 *
 * @param <R> The result type produced when the task completes.
 * @see Task
 * @see TaskTemplate
 */
public sealed interface TaskDefinition<R> permits Task, TaskTemplate {

  /** The name of this task definition — a stable identifier for the task type. */
  String name();

  /** The description of this task — what kind of work it represents. */
  String description();

  /** The expected result type. */
  Class<R> resultType();
}
