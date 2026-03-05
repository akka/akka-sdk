/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.JWT;
import akka.javasdk.keyvalueentity.KeyValueEntity;

// @JWT on a method of a non-HttpEndpoint component - should fail
@Component(id = "jwt-on-component-method-entity")
public class JwtOnComponentMethod extends KeyValueEntity<String> {

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)
  public Effect<String> get() {
    return effects().reply(currentState());
  }
}
