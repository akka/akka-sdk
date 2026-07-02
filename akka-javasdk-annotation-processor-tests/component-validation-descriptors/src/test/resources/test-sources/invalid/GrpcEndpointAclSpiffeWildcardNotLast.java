/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;

@GrpcEndpoint
// ** is only allowed as the final token
@Acl(allow = @Acl.Matcher(spiffe = "svc/**/agent"))
public class GrpcEndpointAclSpiffeWildcardNotLast {

  public String someMethod() {
    return "ok";
  }
}
