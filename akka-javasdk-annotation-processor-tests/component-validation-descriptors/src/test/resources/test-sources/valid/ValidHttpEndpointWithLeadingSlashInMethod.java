package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/api")
public class ValidHttpEndpointWithLeadingSlashInMethod {

  // Method path has leading slash - should be normalized
  @Get("/users/{id}")
  public String get(String id) {
    return "user " + id;
  }

  // Method path without leading slash - should also work
  @Get("items/{id}")
  public String getItem(String id) {
    return "item " + id;
  }
}
