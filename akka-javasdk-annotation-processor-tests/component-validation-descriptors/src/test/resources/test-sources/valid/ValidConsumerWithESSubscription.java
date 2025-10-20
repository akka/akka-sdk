package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-valid-es")
@Consume.FromEventSourcedEntity(MyEventSourcedEntity.class)
public class ValidConsumerWithESSubscription extends Consumer {

  public Effect onCreated(String event) {
    return effects().done();
  }

  public Effect onUpdated(Integer event) {
    return effects().done();
  }
}
