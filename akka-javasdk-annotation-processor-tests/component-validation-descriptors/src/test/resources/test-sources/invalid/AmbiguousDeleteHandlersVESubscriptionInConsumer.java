package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-ambiguous-delete")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class AmbiguousDeleteHandlersVESubscriptionInConsumer extends Consumer {

  @DeleteHandler
  public Effect methodOne() {
    return effects().ignore();
  }

  @DeleteHandler
  public Effect methodTwo() {
    return effects().ignore();
  }
}
