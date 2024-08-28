/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import com.example.wiring.eventsourcedentities.counter.CounterEvent.ValueIncreased;
import com.example.wiring.eventsourcedentities.counter.CounterEvent.ValueMultiplied;
import akka.javasdk.Metadata;
import akka.javasdk.annotations.Produce;
import akka.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.javasdk.impl.MetadataImpl.CeSubject;

@ComponentId("publish-es-to-topic")
@Consume.FromEventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true)
@Produce.ToTopic(PublishESToTopic.COUNTER_EVENTS_TOPIC)
public class PublishESToTopic extends Consumer {

  public static final String COUNTER_EVENTS_TOPIC = "counter-events";
  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect handleIncrease(ValueIncreased increased) {
    return publish(increased);
  }

  public Effect handleMultiply(ValueMultiplied multiplied) {
    return publish(multiplied);
  }

  private Effect publish(CounterEvent counterEvent) {
    String entityId = messageContext().metadata().get(CeSubject()).orElseThrow();
    logger.info("Publishing to " + COUNTER_EVENTS_TOPIC + " event: " + counterEvent + " from " + entityId);
    return effects().produce(counterEvent, Metadata.EMPTY.add(CeSubject(), entityId));
  }
}
