package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-invalid-snapshot-too-many-params")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
public class ConsumerSnapshotHandlerTooManyParams extends Consumer {

  @SnapshotHandler
  public Effect onSnapshot(Integer snapshot, String extra) {
    return effects().done();
  }

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().done();
  }

}
