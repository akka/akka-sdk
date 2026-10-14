package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.StartFrom;
import akka.javasdk.consumer.Consumer;

@Component(id = "invalid-consumerstartfrominvalidtimestamp")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
@StartFrom(timestamp = "yesterday")
public class ConsumerStartFromInvalidTimestamp extends Consumer {

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().done();
  }

}
