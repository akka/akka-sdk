/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.grpc;

import akka.annotation.DoNotInherit;
import akka.grpc.javadsl.Metadata;
import akka.javasdk.Context;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.Tracing;

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

  /** @return The JWT claims, if any, associated with this request. */
  JwtClaims getJwtClaims();

  /** @return The metadata associated with the request being processed */
  Metadata metadata();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();
}
