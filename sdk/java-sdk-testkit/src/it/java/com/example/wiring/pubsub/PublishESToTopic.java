/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import akka.platform.javasdk.annotations.ComponentId;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import com.example.wiring.eventsourcedentities.counter.CounterEvent.ValueIncreased;
import com.example.wiring.eventsourcedentities.counter.CounterEvent.ValueMultiplied;
import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.platform.javasdk.impl.MetadataImpl.CeSubject;

@ComponentId("publish-es-to-topic")
@Consume.FromEventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true)
public class PublishESToTopic extends Action {

  public static final String COUNTER_EVENTS_TOPIC = "counter-events";
  private Logger logger = LoggerFactory.getLogger(getClass());

  @Produce.ToTopic(COUNTER_EVENTS_TOPIC)
  public Effect<CounterEvent> handleIncrease(ValueIncreased increased) {
    return publish(increased);
  }

  @Produce.ToTopic(COUNTER_EVENTS_TOPIC)
  public Effect<CounterEvent> handleMultiply(ValueMultiplied multiplied) {
    return publish(multiplied);
  }

  private Effect<CounterEvent> publish(CounterEvent counterEvent) {
    String entityId = messageContext().metadata().get(CeSubject()).orElseThrow();
    logger.info("Publishing to " + COUNTER_EVENTS_TOPIC + " event: " + counterEvent + " from " + entityId);
    return effects().reply(counterEvent, Metadata.EMPTY.add(CeSubject(), entityId));
  }
}
