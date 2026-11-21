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
   * A client for a remote agent configured under {@code akka.javasdk.a2a.remote-agents.<name>},
   * with the credentials and TLS settings of that configuration.
   *
   * @throws IllegalArgumentException if the name is not configured or its configuration is invalid
   */
  RemoteA2aAgentClient forAgent(String name);

  /**
   * A client for a remote agent given by the URL of its Agent Card. The agent has no credentials
   * and no custom TLS settings, and uses the settings under {@code akka.javasdk.a2a.defaults}.
   *
   * @param cardUrl the URL of the Agent Card, which must start with {@code http://} or {@code
   *     https://}
   * @throws IllegalArgumentException if the URL is not an http or https URL
   */
  RemoteA2aAgentClient forCardUrl(String cardUrl);

  /**
   * A client for the A2A endpoint of another Akka service in the same project, reached by its
   * service name in the way an {@link akka.javasdk.http.HttpClientProvider} reaches it. The calls
   * carry the identity of this service, so the other service's {@code @Acl} can allow it. The agent
   * uses the settings under {@code akka.javasdk.a2a.defaults}.
   *
   * @param serviceName the name of the other service
   */
  RemoteA2aAgentClient forService(String serviceName);
}
