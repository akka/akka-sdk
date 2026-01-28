package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/{id}/my-endpoint")
public class HttpEndpointTooManyParams {

  // Too many parameters - 'id' is path param, but 'value' and 'body' are extras (max 1 extra for body)
  @Get("/too-many")
  public String list5(String id, String value, String body) {
    return "list";
  }
}
