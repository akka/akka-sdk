/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.pubsub;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;
import akkajavasdk.components.keyvalueentities.customer.CustomerEntity;
import akka.javasdk.Metadata;
import akka.javasdk.annotations.Produce;
import akka.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.javasdk.impl.MetadataImpl.CeSubject;
import static akkajavasdk.components.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;

//@Profile({"docker-it-test", "eventing-testkit-destination"})
@ComponentId("publish-ve-to-topic")
@Consume.FromKeyValueEntity(CustomerEntity.class)
@Produce.ToTopic(CUSTOMERS_TOPIC)
public class PublishVEToTopic extends Consumer {

  public static final String CUSTOMERS_TOPIC = "customers";
  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect handleChange(CustomerEntity.Customer customer) {
    String entityId = messageContext().metadata().get(CeSubject()).orElseThrow();
    logger.info("Publishing to " + CUSTOMERS_TOPIC + " message: " + customer + " from " + entityId);
    return effects().produce(customer, Metadata.EMPTY.add(CeSubject(), entityId));
  }
}