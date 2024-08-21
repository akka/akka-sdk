/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;


import akka.platform.javasdk.JsonSupport;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.consumer.Consumer;
import com.example.wiring.keyvalueentities.customer.CustomerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.example.wiring.pubsub.PublishBytesToTopic.CUSTOMERS_BYTES_TOPIC;

@ComponentId("subscribe-to-bytes-from-topic")
@Consume.FromTopic(CUSTOMERS_BYTES_TOPIC)
public class SubscribeToBytesFromTopic extends Consumer {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect handleChange(byte[] payload) {
    try {
      logger.info("Consuming raw bytes: " + new String(payload));
      CustomerEntity.Customer customer = JsonSupport.getObjectMapper().readerFor(CustomerEntity.Customer.class).readValue(payload);
      DummyCustomerStore.store(CUSTOMERS_BYTES_TOPIC, customer.name(), customer);
      return effects().done();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
