package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-ambiguous-es")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
public class PrivateHandlersESSubscriptionInConsumer extends Consumer {

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent message) {
    return effects().produce(message);
  }

  Effect onEvent(Integer message) {
    return effects().produce(message);
  }
}
