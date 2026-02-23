package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/{id}/my-endpoint")
public class HttpEndpointWildcardNotLast {

  // Wildcard ** is not at the last segment
  @Get("/wildcard/**/not/last")
  public String invalidWildcard(String id) {
    return "wildcard";
  }
}
