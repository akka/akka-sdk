package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-no-param")
@Consume.FromTopic("my-topic")
public class ConsumerWithNoParamSubscriptionMethod extends Consumer {
  // Subscription method has no parameters and is not a delete handler - should fail

  public Effect onMessage() {
    return effects().done();
  }
}
