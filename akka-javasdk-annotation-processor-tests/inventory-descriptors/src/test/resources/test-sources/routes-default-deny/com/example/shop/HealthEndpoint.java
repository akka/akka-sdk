package com.example.shop;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

@HttpEndpoint("/health/")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL), denyCode = -1)
public class HealthEndpoint {

  @Get("live")
  public String live() {
    return "live";
  }
}
