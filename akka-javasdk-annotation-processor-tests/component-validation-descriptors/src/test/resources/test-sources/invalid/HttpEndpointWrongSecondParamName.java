package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/{id}/my-endpoint")
public class HttpEndpointWrongSecondParamName {

  // Correct 'id' but second path param 'bob' doesn't match 'value'
  @Get("/something/{bob}")
  public String list3(String id, String value) {
    return "list";
  }
}
