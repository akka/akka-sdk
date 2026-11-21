/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;

/**
 * INTERNAL API
 *
 * <p>A remote A2A agent as the state of a component stores it, so that a client for the agent can
 * be created again after a restart.
 *
 * @param kind {@code named}, {@code card-url} or {@code service}
 * @param value the configured name, the URL of the Agent Card, or the service name
 */
@InternalApi
public record A2aRemoteRef(String kind, String value) {}
