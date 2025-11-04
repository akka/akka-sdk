package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-raw-kve")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class ValidConsumerWithRawEventHandlerKVE extends Consumer {

  // Raw handler catches all events
  public Effect onRawEvent(byte[] bytes) {
    return effects().done();
  }
}
