/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

/**
 * INTERNAL API
 *
 * <p>Serves the Agent Card of the service's A2A endpoint at the well-known path. The SDK registers
 * it with the {@code @Acl} of the A2A endpoint and without its {@code @JWT}, so a client can read
 * the card before it has a token.
 */
@InternalApi
@HttpEndpoint("/.well-known")
public final class A2aAgentCardEndpoint {

  private final A2aRequestHandler handler;

  public A2aAgentCardEndpoint(A2aRequestHandler handler) {
    this.handler = handler;
  }

  @Get("/agent-card.json")
  public HttpResponse agentCard() {
    return handler.agentCard();
  }
}
