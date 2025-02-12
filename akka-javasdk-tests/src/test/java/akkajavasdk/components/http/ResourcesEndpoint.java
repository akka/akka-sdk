/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;

@HttpEndpoint()
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ResourcesEndpoint {

  @Get("index.html")
  public HttpResponse oneSpecificResournce() {
    return HttpResponses.staticResource("index.html");
  }

  @Get("static/**")
  public HttpResponse allTheResources(HttpRequest request) {
    return HttpResponses.staticResource(request, "/static");
  }

}
