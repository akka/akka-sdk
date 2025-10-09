/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.hierarchy;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;

@Component(id = "es-hierarchy-text-consumer")
@Consume.FromEventSourcedEntity(value = TextEsEntity.class)
public class TextEsConsumer extends AbstractTextConsumer {

  public Effect onEvent(TextEsEntity.TextSet event) {
    onText(event.value());
    return effects().done();
  }
}
