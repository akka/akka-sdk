package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-raw-es")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
public class ValidConsumerWithRawEventHandlerES extends Consumer {

  // Specific handler takes precedence
  public Effect onSpecificEvent(SimpleEventSourcedEntity.IncrementCounter event) {
    return effects().done();
  }

  // Raw handler catches all other events (like DecrementCounter)
  public Effect onRawEvent(byte[] bytes) {
    return effects().done();
  }
}
