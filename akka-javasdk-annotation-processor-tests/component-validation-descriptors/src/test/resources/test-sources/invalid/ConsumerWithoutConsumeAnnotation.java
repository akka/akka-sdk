package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-without-consume")
public class ConsumerWithoutConsumeAnnotation extends Consumer {
  // Missing @Consume annotation - should fail validation

  public Effect onEvent(String event) {
    return effects().done();
  }
}
