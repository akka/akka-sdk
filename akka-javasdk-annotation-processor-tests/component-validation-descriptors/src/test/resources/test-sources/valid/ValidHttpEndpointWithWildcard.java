package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/static")
public class ValidHttpEndpointWithWildcard {

  @Get("/**")
  public String serveStaticResource() {
    return "static";
  }
}
