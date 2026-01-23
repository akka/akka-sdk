/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "kv-entity-duplicate-handlers")
public class KeyValueEntityDuplicateHandlers extends KeyValueEntity<String> {

  // Duplicate command handler names (overloaded) - not allowed
  public Effect<String> execute(String cmd) {
    return effects().reply(cmd);
  }

  public Effect<String> execute(Integer cmd) {
    return effects().reply(cmd.toString());
  }
}
