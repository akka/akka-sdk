package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "valid-validconsumerstartfromtimestamp")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
@Consume.StartFrom(timestamp = "2026-10-01T00:00:00Z")
public class ValidConsumerStartFromTimestamp extends Consumer {

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().done();
  }

}
