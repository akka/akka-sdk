/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.keyvalueentities.hierarchy;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;

@ComponentId("kv-hierarchy-text-consumer")
@Consume.FromKeyValueEntity(value = TextKvEntity.class)
public class TextKvConsumer extends AbstractTextConsumer {

  public Effect onEvent(TextKvEntity.State state) {
    onText(state.value());
    return effects().done();
  }
}
