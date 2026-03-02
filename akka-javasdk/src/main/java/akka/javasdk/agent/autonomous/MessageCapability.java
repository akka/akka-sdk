/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/** Capability to send and receive messages with team members. */
public record MessageCapability(String teamId) implements Capability {}
