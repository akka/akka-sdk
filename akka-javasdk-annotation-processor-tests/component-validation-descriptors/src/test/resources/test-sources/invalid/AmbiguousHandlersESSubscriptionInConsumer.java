package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-ambiguous-es")
@Consume.FromEventSourcedEntity(MyEventSourcedEntity.class)
public class AmbiguousHandlersESSubscriptionInConsumer extends Consumer {

  public Effect methodOne(Integer message) {
    return effects().produce(message);
  }

  public Effect methodTwo(Integer message) {
    return effects().produce(message);
  }
}
