package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Delete;

@HttpEndpoint("/api/users")
public class ValidHttpEndpoint {

  @Get("/")
  public String list() {
    return "list";
  }

  @Get("/{id}")
  public String get(String id) {
    return "get " + id;
  }

  @Post("/{id}")
  public String create(String id, String body) {
    return "create " + id;
  }

  @Put("/{id}")
  public String update(String id, String body) {
    return "update " + id;
  }

  @Patch("/{id}")
  public String patch(String id, String body) {
    return "patch " + id;
  }

  @Delete("/{id}")
  public void delete(String id) {
  }
}
