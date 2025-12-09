package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "consumer-multi-delete")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class ConsumerWithMultipleDeleteHandlers extends Consumer {
  // Has multiple delete handlers - should fail

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
