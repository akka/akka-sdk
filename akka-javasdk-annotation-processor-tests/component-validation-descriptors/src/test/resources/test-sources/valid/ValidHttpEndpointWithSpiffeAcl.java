/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;

@HttpEndpoint("/api")
// trailing ** is valid: matches all components of the checkout service
@Acl(allow = @Acl.Matcher(spiffe = "svc/checkout/**"))
public class ValidHttpEndpointWithSpiffeAcl {

  @Get("/")
  public String list() {
    return "list";
  }

  // single-segment wildcards are valid anywhere
  @Acl(allow = @Acl.Matcher(spiffe = "svc/*/agent/*"))
  @Post("/")
  public String create(String body) {
    return "create";
  }
}
