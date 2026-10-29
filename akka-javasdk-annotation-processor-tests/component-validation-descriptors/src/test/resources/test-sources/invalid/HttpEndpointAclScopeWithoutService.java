package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

@HttpEndpoint("/api")
public class HttpEndpointAclScopeWithoutService {

  @Acl(allow = @Acl.Matcher(spiffe = "svc/checkout/*", scope = Acl.Scope.PARENT_PROJECT))
  @Get("/")
  public String list() {
    return "list";
  }
}
