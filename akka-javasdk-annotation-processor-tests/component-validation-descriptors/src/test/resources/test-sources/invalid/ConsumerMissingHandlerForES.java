package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-missing-es-handler")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
public class ConsumerMissingHandlerForES extends Consumer {

  // Wrong handler - should accept CounterEvent or its permitted subclasses
  public Effect wrongHandler(String msg) {
    return effects().done();
  }
}