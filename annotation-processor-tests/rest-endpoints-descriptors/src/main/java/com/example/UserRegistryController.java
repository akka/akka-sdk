/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import kalix.javasdk.annotations.http.Endpoint;
import kalix.javasdk.annotations.http.Post;

@Endpoint("/user")
public class UserRegistryController {

  @Post("/{id}")
  public void createUser(String id, String name) {

  }
}
