/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.jwt;

import static java.util.concurrent.CompletableFuture.completedStage;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.RequestContext;
import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more
// limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/missingjwt")
public class MissingJwtEndpoint {

  // Note: leaving this with injected request context rather than extend AbstractHttpEndpoint to
  // keep
  // some test coverage
  RequestContext context;

  public MissingJwtEndpoint(RequestContext context) {
    this.context = context;
  }

  @Get("/")
  public CompletionStage<String> missingjwt() {
    var claims = context.getJwtClaims();
    var issuer = claims.issuer().get();
    var sub = claims.subject().get();
    return completedStage("issuer: " + issuer + ", subject: " + sub);
  }
}
