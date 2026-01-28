/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "valid-kv-entity-one-arg")
public class ValidKeyValueEntityOneArg extends KeyValueEntity<String> {

  public Effect<String> execute(String command) {
    return effects().reply(command);
  }
}
