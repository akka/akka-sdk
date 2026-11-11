package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

@HttpEndpoint("/api")
@Acl(allow = @Acl.Matcher(service = "*", scope = Acl.Scope.PARENT_PROJECT))
public class ValidHttpEndpointWithParentProjectAcl {

  @Acl(deny = @Acl.Matcher(service = "other-service", scope = Acl.Scope.PARENT_PROJECT))
  @Get("/")
  public String list() {
    return "list";
  }
}
