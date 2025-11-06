package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-valid-es")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
public class ValidConsumerWithESSubscription extends Consumer {

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {

    return effects().done();

  }

}
