/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "cart")
@AgentRole("ignored")
public class CartEntity extends KeyValueEntity<String> {

  public Effect<String> set(String value) {
    return effects().updateState(value).thenReply("done");
  }
}
