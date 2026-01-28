package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

// No leading slash in @HttpEndpoint - should be normalized
@HttpEndpoint("api/v1")
public class ValidHttpEndpointWithNoLeadingSlash {

  @Get("/users")
  public String users() {
    return "users";
  }
}
