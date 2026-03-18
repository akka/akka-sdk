/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * A point-in-time snapshot of a task's state, with a typed result. Returned by {@link
 * TaskClient#get()} — a single fetch that provides everything the caller needs.
 *
 * @param <R> The result type of the task.
 */
public record TaskSnapshot<R>(
    TaskStatus status, String description, String instructions, R result, String approvalReason) {}
