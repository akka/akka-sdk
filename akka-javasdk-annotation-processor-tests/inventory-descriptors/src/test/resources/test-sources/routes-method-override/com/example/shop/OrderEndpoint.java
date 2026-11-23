package com.example.shop;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

@HttpEndpoint("/orders")
@Acl(allow = @Acl.Matcher(service = "frontend"), denyCode = 401)
@JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuers = {"issuer-a", "issuer-b"},
    staticClaims = {
      @JWT.StaticClaim(claim = "tenant", values = "acme"),
      @JWT.StaticClaim(claim = "role", pattern = "^ops-\\d+$")
    })
public class OrderEndpoint {

  @Get("/{id}")
  public String getOrder(String id) {
    return id;
  }

  @Delete("/{id}")
  @Acl(deny = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  @JWT(staticClaims = @JWT.StaticClaim(claim = "role", pattern = "^ops-\\d+$"))
  public String cancelOrder(String id) {
    return id;
  }
}
