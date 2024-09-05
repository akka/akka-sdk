/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;

@HttpEndpoint("/hello")
public class HelloController {

  @Get
  public String hello() {
    return "Hello, World!";
  }
}
