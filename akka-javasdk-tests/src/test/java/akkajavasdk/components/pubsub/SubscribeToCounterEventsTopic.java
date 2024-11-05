/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.pubsub;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;
import akka.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("subscribe-to-counter-events-topic")
@Consume.FromTopic(SubscribeToCounterEventsTopic.COUNTER_EVENTS_TOPIC)
public class SubscribeToCounterEventsTopic extends Consumer {

  public static final String COUNTER_EVENTS_TOPIC = "counter_events";

  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect handleIncrease(CounterEvent.ValueIncreased increased) {
    addEvent(increased);
    return effects().ignore();
  }

  public Effect handleMultiply(CounterEvent.ValueMultiplied multiplied) {
    addEvent(multiplied);
    return effects().ignore();
  }

  private void addEvent(CounterEvent counterEvent) {
    var entityId = messageContext().metadata().get("ce-subject").orElseThrow();
    logger.info("Consuming " + counterEvent + " from " + entityId);
    DummyCounterEventStore.store(entityId, counterEvent);
  }
}
