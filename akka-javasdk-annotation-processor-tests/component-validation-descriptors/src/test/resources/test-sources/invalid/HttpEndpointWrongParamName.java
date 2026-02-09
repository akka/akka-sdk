package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/{id}/my-endpoint")
public class HttpEndpointWrongParamName {

  // Parameter name 'bob' doesn't match path variable 'id'
  @Get("/")
  public String list2(String bob) {
    return "list";
  }
}
