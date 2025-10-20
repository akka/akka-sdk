package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-valid-stream-pub")
@Consume.FromTopic("test")
@Produce.ServiceStream(id = "valid-stream-id")
public class ValidConsumerWithStreamPublishing extends Consumer {

  public Effect handle(String msg) {
    return effects().produce(msg);
  }
}
