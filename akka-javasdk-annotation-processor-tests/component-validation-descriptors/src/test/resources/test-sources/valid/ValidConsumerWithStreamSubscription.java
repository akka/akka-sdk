package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-valid-stream")
@Consume.FromServiceStream(id = "my-stream", service = "my-service")
public class ValidConsumerWithStreamSubscription extends Consumer {

  public Effect onMessage(String message) {
    return effects().done();
  }
  public Effect onMessage2(Integer message) {
    return effects().done();
  }
}
