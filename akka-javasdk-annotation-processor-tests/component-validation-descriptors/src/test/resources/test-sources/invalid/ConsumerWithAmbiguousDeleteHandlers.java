package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "consumer-ambiguous-delete")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class ConsumerWithAmbiguousDeleteHandlers extends Consumer {
  // Multiple delete handlers with zero parameters - should be caught as ambiguous handlers

  @DeleteHandler
  public Effect onDelete1() {
    return effects().done();
  }

  @DeleteHandler
  public Effect onDelete2() {
    return effects().done();
  }

  public static class MyKeyValueEntity extends KeyValueEntity<String> {}
}
