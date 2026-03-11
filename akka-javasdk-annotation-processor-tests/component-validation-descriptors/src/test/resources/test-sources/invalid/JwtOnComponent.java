/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.JWT;
import akka.javasdk.keyvalueentity.KeyValueEntity;

// @JWT on a non-HttpEndpoint component - should fail
@Component(id = "jwt-on-component-entity")
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)
public class JwtOnComponent extends KeyValueEntity<String> {

  public Effect<String> get() {
    return effects().reply(currentState());
  }
}
