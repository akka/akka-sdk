package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-empty-stream")
@Consume.FromTopic("my-topic")
@Produce.ServiceStream(id = "")
public class ConsumerWithEmptyStreamId extends Consumer {
  // Stream ID is empty - should fail

  public Effect onMessage(String message) {
    return effects().done();
  }
}
