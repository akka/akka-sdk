/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;

@HttpEndpoint("/user")
public class UserRegistryController {

  @Post("/{id}")
  public void createUser(String id, String name) {}
}
