/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "valid-kv-entity-no-arg")
public class ValidKeyValueEntityNoArg extends KeyValueEntity<String> {

  public Effect<String> execute() {
    return effects().reply("ok");
  }
}
