/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.jwt;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.http.RequestContext;

import java.util.concurrent.CompletionStage;
import static java.util.concurrent.CompletableFuture.completedStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
// tag::bearer-token[]
@HttpEndpoint("/hello")
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "my-issuer-123", staticClaims = { @JWT.StaticClaim(claim = "sub", pattern = "my-subject-123")})
public class HelloJwtEndpoint {
    // end::bearer-token[]

    RequestContext context;
    public HelloJwtEndpoint(RequestContext context){
        this.context = context;
    }

    @Get("/")
    public CompletionStage<String> helloWorld() {
        var claims = context.getJwtClaims();
        var issuer = claims.issuer().get();
        var sub = claims.subject().get();
        return completedStage("issuer: " + issuer + ", subject: " + sub);
    }
}