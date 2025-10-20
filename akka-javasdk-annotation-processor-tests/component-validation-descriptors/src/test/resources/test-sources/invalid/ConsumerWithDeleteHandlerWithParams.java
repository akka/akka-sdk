package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "consumer-delete-with-params")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class ConsumerWithDeleteHandlerWithParams extends Consumer {
  // Delete handler has parameters - should fail

  @DeleteHandler
  public Effect onDelete(String invalidParam) {
    return effects().done();
  }

  public static class MyKeyValueEntity extends KeyValueEntity<String> {}
}
