/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;

@HttpEndpoint("/api")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ValidHttpEndpointWithAcl {

  @Get("/")
  public String list() {
    return "list";
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  @Post("/")
  public String create(String body) {
    return "create";
  }
}
