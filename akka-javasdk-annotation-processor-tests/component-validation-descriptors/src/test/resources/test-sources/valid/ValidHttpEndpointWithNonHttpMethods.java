package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/api")
public class ValidHttpEndpointWithNonHttpMethods {

  // This is a regular HTTP method
  @Get("/users")
  public String users() {
    return "users";
  }

  // This is NOT an HTTP method (no annotation) - should be ignored by validation
  public void nonHttpMethod() {
    // Helper method
  }

  // Another non-HTTP method with parameters
  private String helperMethod(String input, int count, boolean flag) {
    return input + count + flag;
  }
}
