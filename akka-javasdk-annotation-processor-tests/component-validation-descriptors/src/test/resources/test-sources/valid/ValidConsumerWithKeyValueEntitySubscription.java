package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "consumer-valid-kve")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class ValidConsumerWithKeyValueEntitySubscription extends Consumer {

  public Effect onUpdate(Integer state) {
    return effects().done();
  }

  public static class MyKeyValueEntity extends KeyValueEntity<String> {}
}
