/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Patch;

@HttpEndpoint
public class StatusEndpoint {

  @Patch("status")
  public String status(String body) {
    return body;
  }
}
