package com.example.shop;

import akka.javasdk.annotations.GrpcEndpoint;

@GrpcEndpoint
public class ShopGrpcEndpoint implements ShopService {

  @Override
  public String getItem(String id) {
    return id;
  }
}
