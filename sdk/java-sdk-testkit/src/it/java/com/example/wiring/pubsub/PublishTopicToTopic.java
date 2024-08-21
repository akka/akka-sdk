/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.consumer.Consumer;
import com.example.wiring.keyvalueentities.customer.CustomerEntity;
import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.wiring.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static akka.platform.javasdk.impl.MetadataImpl.CeSubject;

@ComponentId("publish-topic-to-topic")
@Consume.FromTopic(CUSTOMERS_TOPIC)
@Produce.ToTopic(PublishTopicToTopic.CUSTOMERS_2_TOPIC)
public class PublishTopicToTopic extends Consumer {

  public static final String CUSTOMERS_2_TOPIC = "customers_2";
  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect handleChange(CustomerEntity.Customer customer) {
    String entityId = messageContext().metadata().get(CeSubject()).orElseThrow();
    logger.info("Publishing to " + CUSTOMERS_2_TOPIC + " message: " + customer + " from " + entityId);
    return effects().produce(customer, Metadata.EMPTY.add(CeSubject(), entityId));
  }
}
