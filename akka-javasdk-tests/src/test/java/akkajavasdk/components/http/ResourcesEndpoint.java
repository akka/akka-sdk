/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpResponses;

@HttpEndpoint()
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ResourcesEndpoint {

  public record SomeRequest(String path) {}

  @Get("index.html")
  public HttpResponse oneSpecificResournce() {
    return HttpResponses.staticResource("index.html");
  }

  @Get("static/**")
  public HttpResponse allTheResources(HttpRequest request) {
    return HttpResponses.staticResource(request, "/static");
  }

  @Post("static-exploit-try")
  public HttpResponse oneSpecificResourceExploit(SomeRequest request) {
    // Not possible to exploit through the wildcard path as Akka HTTP would normalize
    // 'something/somewhere/..' into `something/'
    // already when parsing the request
    return HttpResponses.staticResource(request.path);
  }
}
