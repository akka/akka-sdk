/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * Context provided to {@link TaskPolicy#onAssignment} when a task is being assigned to an agent.
 */
public record TaskAssignmentContext(
    String taskDescription, String instructions, String assigneeAgentId) {}
