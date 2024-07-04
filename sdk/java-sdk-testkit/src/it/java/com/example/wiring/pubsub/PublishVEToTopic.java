/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import com.example.wiring.valueentities.customer.CustomerEntity;
import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.platform.javasdk.impl.MetadataImpl.CeSubject;

//@Profile({"docker-it-test", "eventing-testkit-destination"})
@Consume.FromValueEntity(CustomerEntity.class)
public class PublishVEToTopic extends Action {

  public static final String CUSTOMERS_TOPIC = "customers";
  private Logger logger = LoggerFactory.getLogger(getClass());

  @Produce.ToTopic(CUSTOMERS_TOPIC)
  public Effect<CustomerEntity.Customer> handleChange(CustomerEntity.Customer customer) {
    String entityId = actionContext().metadata().get(CeSubject()).orElseThrow();
    logger.info("Publishing to " + CUSTOMERS_TOPIC + " message: " + customer + " from " + entityId);
    return effects().reply(customer, Metadata.EMPTY.add(CeSubject(), entityId));
  }
}
