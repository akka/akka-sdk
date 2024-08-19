/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.action;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.consumer.Consumer;
import akka.platform.javasdk.eventsourcedentity.TestESEvent;
import akka.platform.javasdk.eventsourcedentity.TestEventSourcedEntity;

@ComponentId("es-sub-action")
@Consume.FromEventSourcedEntity(value = TestEventSourcedEntity.class, ignoreUnknown = true)
public class TestESSubscription extends Consumer {

  public Effect handleEvent2(TestESEvent.Event2 event) {
    return effects().produce(event.newName());
  }

  public Effect handleEvent3(TestESEvent.Event3 event) {
    return effects().produce(event.b());
  }

  public Effect handleEvent4(TestESEvent.Event4 event) {
    return effects().produce(event.anotherString());
  }
}
