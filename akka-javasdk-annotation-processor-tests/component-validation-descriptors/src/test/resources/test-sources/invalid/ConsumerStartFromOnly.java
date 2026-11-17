package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "invalid-consumerstartfromonly")
@Consume.StartFrom(latest = true)
public class ConsumerStartFromOnly extends Consumer {

  public Effect onEvent(String event) {
    return effects().done();
  }

}
