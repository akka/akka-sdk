/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;

@TypeId("simple-key-value")
public class SimpleKeyValueEntity extends KeyValueEntity<SimpleKeyValueEntity.State> {

  record State(String value ) {}

  public Effect<String> create(String value) {
    return effects().updateState(new State(value)).thenReply("done");
  }
}
