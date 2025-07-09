package com.example.api;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;

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

  // map in all the available packaged static resources under /static
  // see src/main/resources in project for actual files
  // tag::static-resource-tree-from-classpath[]
  @Get("/static/**") // <1>
  public HttpResponse webPageResources(HttpRequest request) { // <2>
    return HttpResponses.staticResource(request, "/static/"); // <3>
  }
  // end::static-resource-tree-from-classpath[]

}
