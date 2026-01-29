package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;

// No path in @HttpEndpoint - should default to "/"
@HttpEndpoint("")
public class ValidHttpEndpointEmptyPath {

  @Get("/")
  public String root() {
    return "root";
  }

  @Get("/users")
  public String users() {
    return "users";
  }
}
