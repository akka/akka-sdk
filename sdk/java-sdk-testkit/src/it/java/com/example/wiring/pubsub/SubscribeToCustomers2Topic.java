/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import com.example.wiring.valueentities.customer.CustomerEntity;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.wiring.pubsub.PublishTopicToTopic.CUSTOMERS_2_TOPIC;

@Consume.FromTopic(CUSTOMERS_2_TOPIC)
public class SubscribeToCustomers2Topic extends Action {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private DummyCustomerStore customerStore = new DummyCustomerStore();

  public Effect<CounterEvent> handle(CustomerEntity.Customer customer) {
    var entityId = actionContext().metadata().get("ce-subject").orElseThrow();
    logger.info("Consuming " + customer + " from " + entityId);
    DummyCustomerStore.store(CUSTOMERS_2_TOPIC, entityId, customer);
    return effects().ignore();
  }

}
