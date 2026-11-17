package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "valid-validconsumerstartfromlatest")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
@Consume.StartFrom(latest = true)
public class ValidConsumerStartFromLatest extends Consumer {

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().done();
  }

}
