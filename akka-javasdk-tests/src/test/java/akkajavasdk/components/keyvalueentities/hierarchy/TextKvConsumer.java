/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.hierarchy;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;

@Component(id = "kv-hierarchy-text-consumer")
@Consume.FromKeyValueEntity(value = TextKvEntity.class)
public class TextKvConsumer extends AbstractTextConsumer {

  public Effect onEvent(TextKvEntity.State state) {
    onText(state.value());
    return effects().done();
  }
}
