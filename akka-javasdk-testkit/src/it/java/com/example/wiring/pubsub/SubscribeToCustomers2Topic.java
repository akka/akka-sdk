/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;
import com.example.wiring.keyvalueentities.customer.CustomerEntity;
import akka.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.wiring.pubsub.PublishTopicToTopic.CUSTOMERS_2_TOPIC;

@ComponentId("subscribe-to-customers-2-topic")
@Consume.FromTopic(CUSTOMERS_2_TOPIC)
public class SubscribeToCustomers2Topic extends Consumer {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private DummyCustomerStore customerStore = new DummyCustomerStore();

  public Effect handle(CustomerEntity.Customer customer) {
    var entityId = messageContext().metadata().get("ce-subject").orElseThrow();
    logger.info("Consuming " + customer + " from " + entityId);
    DummyCustomerStore.store(CUSTOMERS_2_TOPIC, entityId, customer);
    return effects().ignore();
  }

}