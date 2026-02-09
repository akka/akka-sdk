package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/api")
class HttpEndpointPackagePrivate {

  @Get("/")
  public String list() {
    return "list";
  }
}
