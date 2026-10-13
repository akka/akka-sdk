/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

@HttpEndpoint("/api")
public class HttpEndpointAclSpiffeWildcardNotLast {

  // ** is only allowed as the final token
  @Acl(allow = @Acl.Matcher(spiffe = "svc/**/agent"))
  @Get("/")
  public String list() {
    return "list";
  }
}
