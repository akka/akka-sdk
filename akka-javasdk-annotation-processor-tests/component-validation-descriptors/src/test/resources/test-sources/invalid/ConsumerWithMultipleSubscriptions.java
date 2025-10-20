package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-multi-sub")
@Consume.FromTopic("topic1")
@Consume.FromServiceStream(id = "stream1", service = "my-service")
public class ConsumerWithMultipleSubscriptions extends Consumer {
  // Has multiple type-level subscriptions - should fail

  public Effect onMessage(String message) {
    return effects().done();
  }
}
