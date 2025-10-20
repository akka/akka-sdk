package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-without-effect")
@Consume.FromTopic("my-topic")
public class ConsumerWithoutEffectMethod extends Consumer {
  // No methods returning Effect - should fail validation

  public void onEvent(String event) {
    // Invalid: returns void instead of Effect
  }
}
