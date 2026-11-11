package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-host-and-platform-service")
@Consume.FromServiceStream(
    id = "my-stream",
    service = "my-service.example.com",
    platformService = "my-platform-service")
public class ConsumerWithHostAndPlatformService extends Consumer {

  public Effect onMessage(String message) {
    return effects().done();
  }
}
