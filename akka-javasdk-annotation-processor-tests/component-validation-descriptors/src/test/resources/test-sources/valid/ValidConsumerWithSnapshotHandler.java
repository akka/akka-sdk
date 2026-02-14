package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-valid-snapshot")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
public class ValidConsumerWithSnapshotHandler extends Consumer {

  @SnapshotHandler
  public Effect onSnapshot(Integer snapshot) {
    return effects().done();
  }

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().done();
  }

}
