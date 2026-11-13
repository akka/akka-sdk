/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.annotations.JWT;

@GrpcEndpoint
@Acl(allow = @Acl.Matcher(service = "*"))
public class ShopGrpcEndpoint implements ShopGrpcService {

  @Override
  public String getItem(String id) {
    return id;
  }

  @Override
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL), denyCode = 5)
  @JWT(
      validate = JWT.JwtMethodMode.BEARER_TOKEN,
      staticClaims = @JWT.StaticClaim(claim = "sub", pattern = "^user-.*$"))
  public String listItems(String filter) {
    return filter;
  }
}
