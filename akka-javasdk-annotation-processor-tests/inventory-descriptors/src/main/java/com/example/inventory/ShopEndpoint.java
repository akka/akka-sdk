/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.NotUsed;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.WebSocket;
import akka.stream.javadsl.Flow;

@HttpEndpoint("/shop")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "shop-issuer")
public class ShopEndpoint extends BaseShopEndpoint {

  @Get
  public String shop() {
    return "shop";
  }

  @Post("/items/{id}")
  @Acl(
      allow = @Acl.Matcher(service = "billing"),
      deny = @Acl.Matcher(spiffe = "spiffe://acme.com/blocked/**"),
      denyCode = 404)
  @JWT(
      validate = JWT.JwtMethodMode.BEARER_TOKEN,
      staticClaims =
          @JWT.StaticClaim(
              claim = "role",
              values = {"admin", "${SHOP_ROLE}"}))
  public String addItem(String id, String item) {
    return item;
  }

  @Delete("items/{id}")
  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "admin-issuer")
  public String removeItem(String id) {
    return id;
  }

  @WebSocket("/updates")
  public Flow<String, String, NotUsed> updates() {
    return Flow.create();
  }

  @Override
  public String legacy() {
    return "moved";
  }
}
