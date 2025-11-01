package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-ambiguous-ve")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class AmbiguousHandlersVESubscriptionInConsumer extends Consumer {

  public Effect methodOne(Integer message) {
    return effects().produce(message);
  }

  public Effect methodTwo(Integer message) {
    return effects().produce(message);
  }
}
