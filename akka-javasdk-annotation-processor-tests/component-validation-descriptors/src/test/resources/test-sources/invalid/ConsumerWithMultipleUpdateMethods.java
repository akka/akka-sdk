package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "consumer-multi-update")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class ConsumerWithMultipleUpdateMethods extends Consumer {
  // Has multiple update methods for state subscription - should fail

  public Effect onUpdate1(String state) {
    return effects().done();
  }

  public Effect onUpdate2(String state) {
    return effects().done();
  }

  public static class MyKeyValueEntity extends KeyValueEntity<String> {}
}
