/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.hierarchy;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;

@ComponentId("es-hierarchy-text-consumer")
@Consume.FromEventSourcedEntity(value = TextEsEntity.class)
public class TextEsConsumer extends AbstractTextConsumer {

  public Effect onEvent(TextEsEntity.TextSet event) {
    onText(event.value());
    return effects().done();
  }
}
