/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.wiring.pubsub.PublishESToTopic.COUNTER_EVENTS_TOPIC;

@Consume.FromTopic(COUNTER_EVENTS_TOPIC)
public class SubscribeToCounterEventsTopic extends Action {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect<CounterEvent> handleIncrease(CounterEvent.ValueIncreased increased) {
    addEvent(increased);
    return effects().ignore();
  }

  public Effect<CounterEvent> handleMultiply(CounterEvent.ValueMultiplied multiplied) {
    addEvent(multiplied);
    return effects().ignore();
  }

  private void addEvent(CounterEvent counterEvent) {
    var entityId = actionContext().metadata().get("ce-subject").orElseThrow();
    logger.info("Consuming " + counterEvent + " from " + entityId);
    DummyCounterEventStore.store(entityId, counterEvent);
  }
}
