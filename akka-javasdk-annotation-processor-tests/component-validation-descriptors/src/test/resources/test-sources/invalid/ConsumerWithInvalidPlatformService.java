package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-invalid-platform-service")
@Consume.FromServiceStream(id = "my-stream", service = "my-service", platformService = "My_Service")
public class ConsumerWithInvalidPlatformService extends Consumer {

  public Effect onMessage(String message) {
    return effects().done();
  }
}
