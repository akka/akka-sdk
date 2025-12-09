package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-ambiguous-stream")
@Consume.FromServiceStream(id = "source", service = "a")
public class AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer extends Consumer {

  public Effect methodOne(Integer message) {
    return effects().produce(message);
  }

  public Effect methodTwo(Integer message) {
    return effects().produce(message);
  }
}
