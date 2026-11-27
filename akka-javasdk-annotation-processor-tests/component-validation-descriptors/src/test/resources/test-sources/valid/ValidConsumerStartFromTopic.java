package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "valid-validconsumerstartfromtopic")
@Consume.FromTopic(value = "my-topic", consumerGroup = "my-group")
@Consume.StartFrom(latest = true)
public class ValidConsumerStartFromTopic extends Consumer {

  public Effect onEvent(String event) {
    return effects().done();
  }

}
