package com.example.shop;

import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

@HttpEndpoint("status")
public class StatusEndpoint {

  @Get
  public String status() {
    return "ok";
  }
}
