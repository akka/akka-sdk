/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/**
 * Capability to request external input. When enabled, the agent gets access to the {@code
 * requestDecision} tool, which pauses the task until external input is provided. The input can come
 * from a human, another agent, or any external system.
 */
public record ExternalInputCapability() implements Capability {}
