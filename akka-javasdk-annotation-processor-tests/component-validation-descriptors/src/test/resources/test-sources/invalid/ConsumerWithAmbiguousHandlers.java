package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-ambiguous")
@Consume.FromTopic("my-topic")
public class ConsumerWithAmbiguousHandlers extends Consumer {
  // Multiple handlers for the same type - should fail

  public Effect onMessage1(String message) {
    return effects().done();
  }

  public Effect onMessage2(String message) {
    return effects().done();
  }
}
