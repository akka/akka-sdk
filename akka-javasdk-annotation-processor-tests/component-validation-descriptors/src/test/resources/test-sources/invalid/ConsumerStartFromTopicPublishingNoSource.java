package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;

@Component(id = "invalid-consumerstartfromtopicpublishingnosource")
@Produce.ToTopic("output-topic")
@Consume.StartFrom(latest = true)
public class ConsumerStartFromTopicPublishingNoSource extends Consumer {

  public Effect onMessage(String message) {
    return effects().done();
  }

}
