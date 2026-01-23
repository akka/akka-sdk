/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "kv-entity-two-args")
public class KeyValueEntityTwoArgs extends KeyValueEntity<String> {

  // Command handler with 2 arguments - not allowed
  public Effect<String> execute(String cmd, int i) {
    return effects().reply(cmd);
  }
}
