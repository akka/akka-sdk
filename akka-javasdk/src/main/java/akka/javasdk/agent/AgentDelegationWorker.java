/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.DoNotInherit;
import akka.javasdk.agent.autonomous.AutonomousAgent;

/**
 * Marker interface implemented by agent types that can act as delegation workers. Allows {@link
 * akka.javasdk.agent.autonomous.capability.Delegation#to} to accept both {@link Agent} and {@link
 * AutonomousAgent} subclasses in a single call.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface AgentDelegationWorker {}
