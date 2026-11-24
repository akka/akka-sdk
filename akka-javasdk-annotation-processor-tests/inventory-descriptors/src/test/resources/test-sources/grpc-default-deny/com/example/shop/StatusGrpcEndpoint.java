package com.example.shop;

import akka.javasdk.annotations.GrpcEndpoint;

@GrpcEndpoint
public class StatusGrpcEndpoint implements StatusService {

  @Override
  public String status(String in) {
    return in;
  }
}
