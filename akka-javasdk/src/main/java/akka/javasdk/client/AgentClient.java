/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;

/** Not for user extension */
@DoNotInherit
public interface AgentClient {

  /** The agent participates in a session, which is used for the agent's conversational memory. */
  AgentClientInSession inSession(String sessionId);
}
