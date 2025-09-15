/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.jwt;

import static java.util.concurrent.CompletableFuture.completedStage;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;
import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more
// limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
// tag::bearer-token[]
@HttpEndpoint("/hello")
@JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuers = {"my-issuer-123", "${ISSUER_ENV_VALUE}"},
    staticClaims = {@JWT.StaticClaim(claim = "sub", pattern = "my-subject-123")})
public class HelloJwtEndpoint extends AbstractHttpEndpoint {
  // end::bearer-token[]

  @Get("/")
  public CompletionStage<String> helloWorld() {
    var claims = requestContext().getJwtClaims();
    var issuer = claims.issuer().get();
    var sub = claims.subject().get();
    return completedStage("issuer: " + issuer + ", subject: " + sub);
  }
}
