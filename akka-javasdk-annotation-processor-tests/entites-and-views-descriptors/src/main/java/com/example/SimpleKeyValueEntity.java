/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "simple-key-value")
public class SimpleKeyValueEntity extends KeyValueEntity<SimpleKeyValueEntity.State> {

  record State(String value) {}

  public Effect<String> create(String value) {
    return effects().updateState(new State(value)).thenReply("done");
  }
}
