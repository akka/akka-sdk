package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/api")
class HttpEndpointNotPublic {

  @Get("/")
  public String list() {
    return "list";
  }
}
