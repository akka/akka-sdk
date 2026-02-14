package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-invalid-multiple-snapshot")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
public class ConsumerWithMultipleSnapshotHandlers extends Consumer {

  @SnapshotHandler
  public Effect onSnapshot1(Integer snapshot) {
    return effects().done();
  }

  @SnapshotHandler
  public Effect onSnapshot2(Integer snapshot) {
    return effects().done();
  }

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().done();
  }

}
