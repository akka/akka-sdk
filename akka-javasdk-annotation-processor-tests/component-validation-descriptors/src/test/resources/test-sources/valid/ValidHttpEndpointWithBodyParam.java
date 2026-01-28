package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;

@HttpEndpoint("/{id}/my-endpoint")
public class ValidHttpEndpointWithBodyParam {

  // Valid: 'id' matches path param, 'body' is the request body
  @Get("/something")
  public String list4(String id, String body) {
    return "list";
  }

  // Valid: no path param in method path, just 'id' from class-level path
  @Post("/create")
  public String create(String id, String requestBody) {
    return "create";
  }
}
