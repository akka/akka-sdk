/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.annotations.JWT;

@GrpcEndpoint
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)
public class ValidGrpcEndpointWithJwt {

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "my-issuer")
  public String someMethod() {
    return "ok";
  }
}
