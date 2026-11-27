/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.annotation.InternalApi;

/**
 * Base class for A2A (Agent2Agent protocol) endpoints, annotated with {@link
 * akka.javasdk.annotations.a2a.A2aEndpoint}.
 *
 * <p>The endpoint receives all messages for the agent. A2A has no skill selector, so the endpoint
 * decides which component handles a message.
 *
 * <pre>{@code
 * @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
 * @A2aEndpoint(name = "Claims", description = "Answers questions about claims", version = "1.0.0")
 * public class ClaimsA2aEndpoint extends AbstractA2aEndpoint {
 *   private final ComponentClient componentClient;
 *
 *   public ClaimsA2aEndpoint(ComponentClient componentClient) {
 *     this.componentClient = componentClient;
 *   }
 *
 *   @Override
 *   public A2aReply onMessage(A2aMessage message) {
 *     var answer = componentClient
 *         .forAgent()
 *         .inSession(requestContext().sessionId())
 *         .method(ClaimStatusAgent::ask)
 *         .invoke(message.text());
 *     return A2aReply.text(answer);
 *   }
 * }
 * }</pre>
 */
public abstract class AbstractA2aEndpoint {
  private volatile A2aRequestContext context;

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public final void _internalSetRequestContext(A2aRequestContext context) {
    this.context = context;
  }

  /** Always available from {@link #onMessage}, not available from the constructor. */
  protected final A2aRequestContext requestContext() {
    if (context == null) {
      throw new IllegalStateException(
          "The request context can only be accessed from the request handling methods of the"
              + " endpoint.");
    }
    return context;
  }

  /**
   * Handle a message from a caller.
   *
   * <p>Throwing an exception gives the caller an internal error without details.
   */
  public abstract A2aReply onMessage(A2aMessage message);
}
