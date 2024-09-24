package com.example.acl;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;


// tag::endpoint-class[]
@Acl(allow = @Acl.Matcher(service = "shopping-cart"))
@HttpEndpoint("/payments")
public class PaymentEndpoint {
  //...
}
// end::endpoint-class[]