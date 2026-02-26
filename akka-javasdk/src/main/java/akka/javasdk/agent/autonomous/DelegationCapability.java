/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/** Capability to delegate subtasks to other autonomous agents. */
public record DelegationCapability(
    String agentClassName, String agentComponentId, String description) implements Capability {}
