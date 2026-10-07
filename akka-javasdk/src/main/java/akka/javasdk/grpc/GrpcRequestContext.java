/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.grpc;

import akka.annotation.DoNotInherit;
import akka.grpc.javadsl.Metadata;
import akka.javasdk.Context;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.SpiffeContext;
import akka.javasdk.Tracing;
import java.util.Optional;

/**
 * Not for user extension, can be injected as constructor parameter into gRPC endpoint components
 */
@DoNotInherit
public interface GrpcRequestContext extends Context {

  /**
   * Get the principals associated with this request.
   *
   * @return The principals associated with this request.
   */
  Principals getPrincipals();

  /**
   * @return The JWT claims, if any, associated with this request.
   */
  JwtClaims getJwtClaims();

  /**
   * @return The metadata associated with the request being processed
   */
  Metadata metadata();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();

  /**
   * The SPIFFE context for this endpoint, including the caller's identity if the request came from
   * another Akka service in the same project. Empty when SPIFFE is disabled.
   */
  Optional<SpiffeContext> getSpiffeContext();
}
