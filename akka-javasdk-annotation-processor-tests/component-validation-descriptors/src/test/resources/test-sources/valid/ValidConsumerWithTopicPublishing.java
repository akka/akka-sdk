package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-valid-publish")
@Consume.FromTopic("input-topic")
@Produce.ToTopic("output-topic")
public class ValidConsumerWithTopicPublishing extends Consumer {

  public Effect onMessage(String message) {
    return effects().done();
  }
}
