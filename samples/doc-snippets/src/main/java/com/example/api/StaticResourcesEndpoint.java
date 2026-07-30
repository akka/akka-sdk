package com.example.api;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;
import java.nio.charset.StandardCharsets;

@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class StaticResourcesEndpoint {

  // provide a landing page from root
  // tag::single-static-resource-from-classpath[]
  @Get("/") // <1>
  public HttpResponse index() {
    return HttpResponses.staticResource("index.html"); // <2>
  }

  @Get("/favicon.ico") // <3>
  public HttpResponse favicon() {
    return HttpResponses.staticResource("favicon.ico"); // <4>
  }

  // end::single-static-resource-from-classpath[]

  // map in all the available packaged static resources under /pages
  // see src/main/resources in project for actual files
  // tag::static-resource-tree-from-classpath[]
  @Get("/pages/**") // <1>
  public HttpResponse webPageResources(HttpRequest request) { // <2>
    return HttpResponses.staticResource(request, "/pages/"); // <3>
  }

  // end::static-resource-tree-from-classpath[]

  // build an HTML page at request time rather than serving a packaged file
  // tag::generated-html[]
  @Get("/status")
  public HttpResponse status() {
    var html = "<html><body><h1>Service is running</h1></body></html>"; // <1>
    return HttpResponses.of(
      StatusCodes.OK,
      ContentTypes.TEXT_HTML_UTF8,
      html.getBytes(StandardCharsets.UTF_8)
    ); // <2>
  }
  // end::generated-html[]
}
