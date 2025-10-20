package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-valid-delete")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class ValidConsumerWithDeleteHandler extends Consumer {

  public Effect onUpdate(String state) {
    return effects().done();
  }

  @DeleteHandler
  public Effect onDelete() {
    return effects().done();
  }
}
