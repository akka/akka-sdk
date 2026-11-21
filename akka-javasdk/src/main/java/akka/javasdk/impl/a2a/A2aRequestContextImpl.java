/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.SpiffeContext;
import akka.javasdk.Tracing;
import akka.javasdk.a2a.A2aRequestContext;
import akka.javasdk.http.RequestContext;
import java.util.List;
import java.util.Optional;

/**
 * INTERNAL API
 *
 * <p>The {@link A2aRequestContext} of one request: the HTTP request context, and the context id and
 * task id once the SDK knows them.
 */
@InternalApi
public final class A2aRequestContextImpl implements A2aRequestContext {

  private final RequestContext httpContext;
  private volatile String contextId;
  private volatile String taskId;

  public A2aRequestContextImpl(RequestContext httpContext) {
    this.httpContext = httpContext;
  }

  void bind(String contextId, String taskId) {
    this.contextId = contextId;
    this.taskId = taskId;
  }

  @Override
  public Principals getPrincipals() {
    return httpContext.getPrincipals();
  }

  @Override
  public JwtClaims getJwtClaims() {
    return httpContext.getJwtClaims();
  }

  @Override
  public Tracing tracing() {
    return httpContext.tracing();
  }

  @Override
  public Optional<HttpHeader> requestHeader(String headerName) {
    return httpContext.requestHeader(headerName);
  }

  @Override
  public List<HttpHeader> allRequestHeaders() {
    return httpContext.allRequestHeaders();
  }

  @Override
  public Optional<SpiffeContext> getSpiffeContext() {
    return httpContext.getSpiffeContext();
  }

  @Override
  public Optional<String> contextId() {
    return Optional.ofNullable(contextId);
  }

  @Override
  public Optional<String> taskId() {
    return Optional.ofNullable(taskId);
  }
}
