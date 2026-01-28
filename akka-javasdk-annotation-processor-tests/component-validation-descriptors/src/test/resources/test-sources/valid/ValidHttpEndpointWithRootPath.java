package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/")
public class ValidHttpEndpointWithRootPath {

  @Get("/")
  public String root() {
    return "root";
  }

  @Get("a")
  public String a() {
    return "a";
  }

  @Get("/b")
  public String b() {
    return "b";
  }
}
