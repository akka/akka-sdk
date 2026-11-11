package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-valid-platform-stream")
@Consume.FromServiceStream(
    id = "my-stream",
    service = "my-service",
    platformService = "my-platform-service")
public class ValidConsumerWithPlatformServiceStream extends Consumer {

  public Effect onMessage(String message) {
    return effects().done();
  }
}
