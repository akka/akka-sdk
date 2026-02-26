/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/** Capability to interact with a shared task list. */
public record TaskListCapability(String taskListId) implements Capability {}
