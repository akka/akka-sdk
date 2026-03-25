/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;

/**
 * A capability that an autonomous agent can have. Capabilities are declared via factory methods on
 * {@link AutonomousAgent} and passed to {@link AgentDefinition#capabilities}.
 *
 * @see TaskAcceptance
 * @see Delegation
 */
public interface AgentCapability {}
