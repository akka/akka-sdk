/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.annotation.DoNotInherit;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import java.util.Optional;

/**
 * The context of an A2A request. Available from {@link AbstractA2aEndpoint#requestContext()} while
 * the endpoint handles a request.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface A2aRequestContext {

  /** The principals of the caller. */
  Principals getPrincipals();

  /**
   * The claims of the caller's JWT.
   *
   * @throws RuntimeException when the endpoint has no {@link akka.javasdk.annotations.JWT}
   *     annotation
   */
  JwtClaims getJwtClaims();

  /** The A2A context id of the message, once the SDK has read or assigned it. */
  Optional<String> contextId();

  /** The id of the A2A task that the request is about, once the SDK knows it. */
  Optional<String> taskId();
}
