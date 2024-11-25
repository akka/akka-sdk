package hellojwt.api;

import akka.javasdk.annotations.Acl;
// tag::bearer-token[]
import akka.javasdk.annotations.JWT;
// tag::accessing-claims[]
import akka.javasdk.annotations.http.HttpEndpoint;
// end::accessing-claims[]
// end::bearer-token[]

import akka.javasdk.annotations.http.Get;
// tag::accessing-claims[]
import akka.javasdk.http.AbstractHttpEndpoint;

// end::accessing-claims[]

import java.util.concurrent.CompletionStage;
import static java.util.concurrent.CompletableFuture.completedStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
// tag::bearer-token[]
@HttpEndpoint("/hello")
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "my-issuer") // <1>
// tag::accessing-claims[]
public class HelloJwtEndpoint extends AbstractHttpEndpoint {
  // end::bearer-token[]
  // end::accessing-claims[]

  @Get("/")
  public CompletionStage<String> hello() {
    return completedStage("Hello, World!");
  }

  // tag::accessing-claims[]

  // tag::multiple-bearer-token-issuers[]
  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = {"my-issuer", "my-issuer2"}, staticClaims = @JWT.StaticClaim(claim = "sub", values = "my-subject"))
  // end::multiple-bearer-token-issuers[]
  @Get("/claims")
  public CompletionStage<String> helloClaims() {
    var claims = requestContext().getJwtClaims(); // <1>
    var issuer = claims.issuer().get(); // <2>
    var sub = claims.subject().get(); // <2>
    return completedStage("issuer: " + issuer + ", subject: " + sub);
  }
  // tag::bearer-token[]

}
// end::accessing-claims[]
// end::bearer-token[]

