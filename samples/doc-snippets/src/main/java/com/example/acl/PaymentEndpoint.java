package com.example.acl;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;


// tag::endpoint-class[]
@Acl(allow = @Acl.Matcher(service = "shopping-cart"))
@HttpEndpoint("/payments")
public class PaymentEndpoint {
  //...
  // end::endpoint-class[]
  @Get
  public String method() {
    return "Example";
  }
  // tag::endpoint-class[]
}
// end::endpoint-class[]