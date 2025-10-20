package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-too-many-params")
@Consume.FromTopic("my-topic")
public class ConsumerWithTooManyParams extends Consumer {

  public Effect validMethod(String param) {
    return effects().done();
  }

  public Effect invalidMethod(String param1, String param2, String param3) {
    // Invalid: has more than one parameter
    return effects().done();
  }
}
