/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.MetadataContext;
import akka.javasdk.Tracing;

import java.util.Optional;

public interface AgentContext extends MetadataContext {
  /**
   * The agent may participate in a session, which is used for the agent's conversational memory.
   */
  Optional<String> sessionId();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();
}
