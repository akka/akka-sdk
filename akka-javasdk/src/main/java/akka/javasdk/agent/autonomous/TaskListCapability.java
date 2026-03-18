/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/**
 * Capability to interact with a shared task list.
 *
 * @param agentType the member's agent type for task filtering (nullable for standalone use)
 */
public record TaskListCapability(String taskListId, String agentType) implements Capability {}
