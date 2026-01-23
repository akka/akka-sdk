package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-invalid-snapshot-kve")
@Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
public class ConsumerSnapshotHandlerWithKVE extends Consumer {

  @SnapshotHandler
  public Effect onSnapshot(Integer snapshot) {
    return effects().done();
  }

  public Effect onUpdate(Integer state) {
    return effects().done();
  }

}
