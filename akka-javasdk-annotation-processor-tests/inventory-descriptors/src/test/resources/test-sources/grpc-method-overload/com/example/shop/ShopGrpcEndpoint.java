package com.example.shop;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;

@GrpcEndpoint
@Acl(allow = @Acl.Matcher(service = "billing"))
public class ShopGrpcEndpoint implements ShopService {

  public String getItem(String id, boolean verbose) {
    return id;
  }

  @Override
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  public String getItem(String id) {
    return id;
  }
}
