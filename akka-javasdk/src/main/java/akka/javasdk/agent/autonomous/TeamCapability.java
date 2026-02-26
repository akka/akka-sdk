/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/**
 * Capability to form and manage teams — add agent types as team members.
 *
 * @param maxMembers maximum instances of this agent type (0 means unlimited)
 */
public record TeamCapability(
    String agentClassName, String agentComponentId, String description, int maxMembers)
    implements Capability {}
