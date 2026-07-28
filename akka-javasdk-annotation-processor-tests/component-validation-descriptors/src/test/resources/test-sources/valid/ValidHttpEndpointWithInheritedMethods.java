package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;

abstract class BaseHttpEndpoint {

  @Get("/inherited/{id}")
  public String inherited(String id) {
    return "inherited " + id;
  }

  @Post("/inherited")
  public String inheritedCreate(String body) {
    return "created";
  }
}

@HttpEndpoint("/api/inheriting")
public class ValidHttpEndpointWithInheritedMethods extends BaseHttpEndpoint {

  @Get("/own/{id}")
  public String own(String id) {
    return "own " + id;
  }
}
