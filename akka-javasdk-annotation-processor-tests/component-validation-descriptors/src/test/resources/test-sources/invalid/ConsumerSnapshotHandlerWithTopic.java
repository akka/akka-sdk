package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-invalid-snapshot-topic")
@Consume.FromTopic("my-topic")
public class ConsumerSnapshotHandlerWithTopic extends Consumer {

  @SnapshotHandler
  public Effect onSnapshot(String message) {
    return effects().done();
  }

  public Effect onMessage(String message) {
    return effects().done();
  }

}
