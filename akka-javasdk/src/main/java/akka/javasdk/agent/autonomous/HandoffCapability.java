/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/** Capability to hand off the current task to another autonomous agent. */
public record HandoffCapability(String agentClassName, String agentComponentId, String description)
    implements Capability {}
