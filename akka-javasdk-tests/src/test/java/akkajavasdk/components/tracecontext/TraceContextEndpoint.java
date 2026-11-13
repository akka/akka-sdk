/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracecontext;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;
import java.util.concurrent.Executor;

@HttpEndpoint("/trace-context")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TraceContextEndpoint extends AbstractHttpEndpoint {

  private final Executor executor;

  public TraceContextEndpoint(Executor executor) {
    this.executor = executor;
  }

  @Get("/observe")
  public SpanIds observe() {
    return SpanIds.observe(requestContext().tracing(), executor);
  }
}
