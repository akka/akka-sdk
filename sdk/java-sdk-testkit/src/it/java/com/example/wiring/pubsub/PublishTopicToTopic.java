/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import com.example.wiring.keyvalueentities.customer.CustomerEntity;
import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.wiring.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static akka.platform.javasdk.impl.MetadataImpl.CeSubject;

@Consume.FromTopic(CUSTOMERS_TOPIC)
public class PublishTopicToTopic extends Action {

  public static final String CUSTOMERS_2_TOPIC = "customers_2";
  private Logger logger = LoggerFactory.getLogger(getClass());

  @Produce.ToTopic(CUSTOMERS_2_TOPIC)
  public Effect<CustomerEntity.Customer> handleChange(CustomerEntity.Customer customer) {
    String entityId = actionContext().metadata().get(CeSubject()).orElseThrow();
    logger.info("Publishing to " + CUSTOMERS_2_TOPIC + " message: " + customer + " from " + entityId);
    return effects().reply(customer, Metadata.EMPTY.add(CeSubject(), entityId));
  }
}
