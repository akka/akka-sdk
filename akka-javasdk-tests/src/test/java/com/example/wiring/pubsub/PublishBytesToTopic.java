/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;


import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;
import com.example.wiring.keyvalueentities.customer.CustomerEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Produce;
import akka.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("publish-bytes-to-topic")
@Consume.FromKeyValueEntity(CustomerEntity.class)
@Produce.ToTopic(PublishBytesToTopic.CUSTOMERS_BYTES_TOPIC)
public class PublishBytesToTopic extends Consumer {

  public static final String CUSTOMERS_BYTES_TOPIC = "customers_bytes";
  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect handleChange(CustomerEntity.Customer customer) {
    try {
      var payload = JsonSupport.getObjectMapper().writerFor(CustomerEntity.Customer.class).writeValueAsBytes(customer);
      logger.info("Publishing to " + CUSTOMERS_BYTES_TOPIC + " raw bytes: " + new String(payload));
      return effects().produce(payload);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
