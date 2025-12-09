package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-missing-kve-handler")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class ConsumerMissingHandlerForKVE extends Consumer {

  // Wrong handler - should accept Integer (the state type), not String
  public Effect wrongHandler(String msg) {
    return effects().done();
  }
}