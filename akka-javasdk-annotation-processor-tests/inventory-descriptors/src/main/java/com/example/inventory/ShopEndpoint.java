/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

@HttpEndpoint("/shop")
public class ShopEndpoint {

  @Get
  public String shop() {
    return "shop";
  }
}
