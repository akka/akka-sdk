/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;

@GrpcEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ValidGrpcEndpointWithAcl {

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  public String someMethod() {
    return "ok";
  }
}
