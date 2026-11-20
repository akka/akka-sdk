/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.annotation.DoNotInherit;

/**
 * Client for calling remote Agent2Agent (A2A) agents.
 *
 * <p>Inject it into components and endpoints, as with {@link akka.javasdk.http.HttpClientProvider}.
 * Every request carries a {@code X-Akka-Caller} header that names the calling component, when the
 * runtime knows it.
 *
 * <p>Not for user extension, instances provided by the SDK through dependency injection.
 */
@DoNotInherit
public interface A2aClient {

  /**
   * Returns a client for one remote agent.
   *
   * @param nameOrCardUrl either a name configured under {@code
   *     akka.javasdk.a2a.remote-agents.<name>}, or the URL of an Agent Card, which must start with
   *     {@code http://} or {@code https://}. An agent given by URL has no credentials and no custom
   *     TLS settings, and uses the timeouts under {@code akka.javasdk.a2a.defaults}.
   * @throws IllegalArgumentException if the name is not configured or its configuration is invalid
   */
  RemoteA2aAgentClient forAgent(String nameOrCardUrl);
}
