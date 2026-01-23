package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-invalid-snapshot-no-params")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
public class ConsumerSnapshotHandlerNoParams extends Consumer {

  @SnapshotHandler
  public Effect onSnapshot() {
    return effects().done();
  }

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().done();
  }

}
