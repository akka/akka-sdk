package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;

@HttpEndpoint()
public class OpenApiSpecificationEndpoint {

  // tag::serve-openapi[]
  @Get("/openapi.yaml")
  public HttpResponse openApiV1Yaml() {
    return HttpResponses.staticResource("openapi.yaml");
  }
  // end::serve-openapi[]
}
