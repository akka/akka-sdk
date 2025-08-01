/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.grpc;

import akka.annotation.InternalApi;

/**
 * Optional base class for gRPC endpoints giving access to a request context without additional
 * constructor parameters
 */
public abstract class AbstractGrpcEndpoint {

  private volatile GrpcRequestContext context;

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public final void _internalSetRequestContext(GrpcRequestContext context) {
    this.context = context;
  }

  /** Always available from request handling methods, not available from the constructor. */
  protected final GrpcRequestContext requestContext() {
    if (context == null) {
      throw new IllegalStateException(
          "The request context can only be accessed from the request handling methods of the"
              + " endpoint.");
    }
    return context;
  }
}
