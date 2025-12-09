package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "not-public-consumer")
@Consume.FromTopic("test-topic")
class NotPublicConsumer extends Consumer {
  public Effect handle(String msg) {
    return effects().done();
  }
}
