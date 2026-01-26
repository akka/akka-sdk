/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "not-public-kv-entity")
class NotPublicKeyValueEntity extends KeyValueEntity<String> {

  public Effect<String> execute() {
    return effects().reply("ok");
  }
}
