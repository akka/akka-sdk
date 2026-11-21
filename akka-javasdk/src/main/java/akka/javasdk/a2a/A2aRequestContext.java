/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.SpiffeContext;
import akka.javasdk.Tracing;
import java.util.List;
import java.util.Optional;

/**
 * The context of an A2A request. Inject it into the constructor of an {@link AbstractA2aEndpoint}
 * subclass, which the SDK creates for each request, and read it from {@link
 * AbstractA2aEndpoint#agents()}.
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

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();

  /** A header of the request with the given name (case ignored), if present. */
  Optional<HttpHeader> requestHeader(String headerName);

  /** All the headers of the request. */
  List<HttpHeader> allRequestHeaders();

  /**
   * The SPIFFE context of the request, including the caller's identity if the request came from
   * another Akka service in the same project. Empty when SPIFFE is disabled.
   */
  Optional<SpiffeContext> getSpiffeContext();

  /** The A2A context id of the message, once the SDK has read or assigned it. */
  Optional<String> contextId();

  /** The id of the A2A task that the request is about, once the SDK knows it. */
  Optional<String> taskId();
}
