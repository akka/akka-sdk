package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

abstract class BaseHttpEndpointWrongParamName {

  // Parameter name 'bob' doesn't match path variable 'id', and the route is inherited
  // by the concrete endpoint below, so the error must be reported for the subclass too.
  @Get("/{id}")
  public String inherited(String bob) {
    return "inherited";
  }
}

@HttpEndpoint("/my-endpoint")
public class HttpEndpointInheritedWrongParamName extends BaseHttpEndpointWrongParamName {
}
