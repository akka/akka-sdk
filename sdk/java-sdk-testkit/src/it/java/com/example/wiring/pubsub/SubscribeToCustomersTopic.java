/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import akka.platform.javasdk.annotations.ComponentId;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import com.example.wiring.keyvalueentities.customer.CustomerEntity;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.wiring.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;

@ComponentId("subscribe-to-customers-topic")
@Consume.FromTopic(CUSTOMERS_TOPIC)
public class SubscribeToCustomersTopic extends Action {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect<CounterEvent> handle(CustomerEntity.Customer customer) {
    var entityId = actionContext().metadata().get("ce-subject").orElseThrow();
    logger.info("Consuming " + customer + " from " + entityId);
    DummyCustomerStore.store(CUSTOMERS_TOPIC, entityId, customer);
    return effects().ignore();
  }
}
