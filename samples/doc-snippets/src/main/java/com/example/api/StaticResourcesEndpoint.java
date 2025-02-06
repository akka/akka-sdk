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

  // tag::static-resources-from-classpath[]

  // provide a landing page from root
  @Get("/index.html")
  public HttpResponse index() {
    return HttpResponses.resourceFromClassPath("/static/index.html");
  }

  // map in all the available packaged static resources under /static
  // see src/main/resources in project for actual files
  @Get("/static/**")
  public HttpResponse webPageResources(HttpRequest request) {
    return HttpResponses.resourceFromClassPath(request.getUri().getPathString());
  }
  // end::static-resources-from-classpath[]

}
