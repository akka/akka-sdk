package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/{id}/my-endpoint")
public class HttpEndpointMissingPathParam {

  // Missing 'id' parameter - should fail
  @Get("/")
  public String list1() {
    return "list";
  }
}
