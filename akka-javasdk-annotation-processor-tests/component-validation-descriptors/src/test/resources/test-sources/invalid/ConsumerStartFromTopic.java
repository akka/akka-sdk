package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.StartFrom;
import akka.javasdk.consumer.Consumer;

@Component(id = "invalid-consumerstartfromtopic")
@Consume.FromTopic("my-topic")
@StartFrom(latest = true)
public class ConsumerStartFromTopic extends Consumer {

  public Effect onEvent(String event) {
    return effects().done();
  }

}
