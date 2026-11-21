/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.annotation.InternalApi;

/**
 * Base class of an Agent2Agent (A2A) protocol endpoint. Annotate the subclass with {@link
 * akka.javasdk.annotations.a2a.A2aEndpoint} and implement {@link #agents()}.
 *
 * <pre>{@code
 * @A2aEndpoint(name = "Claims", version = "1.0.0")
 * @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
 * @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "https://idp.example.com")
 * public class ClaimsA2aEndpoint extends AbstractA2aEndpoint {
 *   @Override
 *   protected A2aAgents agents() {
 *     return A2aAgents.create().agent(ClaimsAgent.class);
 *   }
 * }
 * }</pre>
 *
 * <p>The SDK serves the A2A methods and the Agent Card. It creates the subclass for each request,
 * so its constructor can accept an {@link A2aRequestContext}, besides the parameters an HTTP
 * endpoint accepts, and {@link #agents()} can depend on the caller.
 *
 * <p>Give an endpoint that more than one client can reach a {@link akka.javasdk.annotations.JWT}
 * annotation. The SDK keeps the tasks and agent sessions of each token subject apart. Without a
 * subject, callers that send the same context id share the agent session, and {@code ListTasks} is
 * refused.
 */
public abstract class AbstractA2aEndpoint {

  /**
   * The agents and task workflows that this endpoint publishes. The SDK calls it for each request
   * and for the Agent Card, and once at startup, without a request, when a workflow takes an {@link
   * A2aTaskContext}. Request values are not available then.
   */
  protected abstract A2aAgents agents();

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public final A2aAgents _internalAgents() {
    return agents();
  }
}
