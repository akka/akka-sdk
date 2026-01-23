package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-invalid-snapshot-stream")
@Consume.FromServiceStream(service = "other-service", id = "events")
public class ConsumerSnapshotHandlerWithServiceStream extends Consumer {

  @SnapshotHandler
  public Effect onSnapshot(String snapshot) {
    return effects().done();
  }

  public Effect onEvent(String event) {
    return effects().done();
  }

}
