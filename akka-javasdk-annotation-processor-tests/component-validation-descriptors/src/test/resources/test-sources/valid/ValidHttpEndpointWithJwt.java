/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;

@HttpEndpoint("/api")
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)
public class ValidHttpEndpointWithJwt {

  @Get("/")
  public String list() {
    return "list";
  }

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "my-issuer")
  @Post("/")
  public String create(String body) {
    return "create";
  }
}
