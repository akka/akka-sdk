package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/api/health")
public class ValidHttpEndpointNoBody {

  @Get("/")
  public String check() {
    return "ok";
  }

  @Get("/ping")
  public String ping() {
    return "pong";
  }
}
