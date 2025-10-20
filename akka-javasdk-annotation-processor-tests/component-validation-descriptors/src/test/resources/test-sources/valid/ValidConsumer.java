package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "valid-consumer")
@Consume.FromTopic("my-topic")
public class ValidConsumer extends Consumer {

  public Effect onEvent(String event) {
    return effects().done();
  }
}
