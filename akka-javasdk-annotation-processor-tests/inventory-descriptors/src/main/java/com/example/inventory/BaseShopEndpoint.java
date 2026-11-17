/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.http.Get;

public abstract class BaseShopEndpoint {

  @Get("/health")
  public String health() {
    return "ok";
  }

  @Get("/legacy")
  public String legacy() {
    return "legacy";
  }
}
