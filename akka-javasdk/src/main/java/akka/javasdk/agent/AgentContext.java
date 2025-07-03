/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.MetadataContext;
import akka.javasdk.Tracing;

import java.util.Optional;

/**
 * Context information available to an agent during command handling.
 * <p>
 * Provides access to session information and tracing capabilities for agents.
 * The context is only available during command processing and will throw an exception
 * if accessed from the agent constructor.
 * <p>
 * <strong>Session Management:</strong>
 * The session ID identifies the contextual context for the agent. Multiple agents
 * can share the same session ID to collaborate on a common goal, sharing session memory
 * and contextual history.
 * <p>
 * <strong>Tracing:</strong>
 * Custom tracing can be added for observability and debugging purposes.
 */
public interface AgentContext extends MetadataContext {
  
  /**
   * Returns the session identifier for this agent interaction.
   * <p>
   * The agent participates in a session, which is used for the agent's contextual memory.
   * Session memory is shared between all agents that use the same session ID, enabling
   * multi-agent collaboration and maintaining context across interactions.
   * 
   * @return the session ID for this agent interaction
   */
  String sessionId();

  /**
   * Provides access to tracing for custom application-specific tracing.
   * <p>
   * Use this to add custom spans and trace information for observability
   * and debugging of agent interactions.
   * 
   * @return tracing interface for custom tracing
   */
  Tracing tracing();
}
