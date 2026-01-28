package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Delete;

@HttpEndpoint("/resources")
public class ValidHttpEndpointAllMethods {

  @Get("/{id}")
  public String get(String id) {
    return "get " + id;
  }

  @Post("/")
  public String post(String body) {
    return "post";
  }

  @Put("/{id}")
  public String put(String id, String body) {
    return "put " + id;
  }

  @Patch("/{id}")
  public String patch(String id, String body) {
    return "patch " + id;
  }

  @Delete("/{id}")
  public void delete(String id) {
  }
}
