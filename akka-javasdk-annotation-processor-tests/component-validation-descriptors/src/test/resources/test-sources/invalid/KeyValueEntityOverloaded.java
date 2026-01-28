/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "kv-entity-overloaded")
public class KeyValueEntityOverloaded extends KeyValueEntity<String> {

  // Overloaded command handlers - not allowed
  public Effect<String> createEntity(String name) {
    return effects().reply(name);
  }

  public Effect<String> createEntity(String name, String email) {
    return effects().reply(name + ":" + email);
  }
}
