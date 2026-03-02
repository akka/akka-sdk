/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * Context provided to {@link TaskPolicy#onCompletion} when a task is being completed. The result is
 * pre-deserialized to the task's result type.
 *
 * @param <R> The result type of the task.
 */
public record TaskCompletionContext<R>(
    R result, String resultJson, String taskDescription, String agentId) {}
