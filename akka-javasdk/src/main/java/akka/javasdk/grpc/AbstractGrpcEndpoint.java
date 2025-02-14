/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.grpc;

import akka.annotation.InternalApi;

/**
 * Optional base class for gRPC endpoints giving access to a request context without additional constructor parameters
 */
abstract public class AbstractGrpcEndpoint {

  volatile private GrpcRequestContext context;

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  final public void _internalSetRequestContext(GrpcRequestContext context) {
    this.context = context;
  }

  /**
   * Always available from request handling methods, not available from the constructor.
   */
  protected final GrpcRequestContext requestContext() {
    if (context == null) {
      throw new IllegalStateException("The request context can only be accessed from the request handling methods of the endpoint.");
    }
    return context;
  }

}
