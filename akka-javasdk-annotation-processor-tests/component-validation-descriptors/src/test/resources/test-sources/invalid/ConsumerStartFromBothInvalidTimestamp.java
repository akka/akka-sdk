package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "invalid-consumerstartfrombothinvalidtimestamp")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
@Consume.StartFrom(latest = true, timestamp = "garbage")
public class ConsumerStartFromBothInvalidTimestamp extends Consumer {

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().done();
  }

}
