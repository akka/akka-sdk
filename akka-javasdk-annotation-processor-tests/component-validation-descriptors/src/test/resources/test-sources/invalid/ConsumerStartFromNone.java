package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "invalid-consumerstartfromnone")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
@Consume.StartFrom()
public class ConsumerStartFromNone extends Consumer {

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().done();
  }

}
