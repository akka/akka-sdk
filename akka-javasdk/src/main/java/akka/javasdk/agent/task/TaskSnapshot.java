/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import java.util.Optional;

/**
 * A point-in-time snapshot of a task's state, with a typed result.
 *
 * @param <R> The result type of the task.
 */
public record TaskSnapshot<R>(
    TaskStatus status,
    String description,
    String instructions,
    Optional<R> result,
    Optional<String> failureReason) {}
