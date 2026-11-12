package com.example.shop;

import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

@HttpEndpoint("/shop")
public class ShopEndpoint {

  @Get("/")
  public String list() {
    return "list";
  }
}
