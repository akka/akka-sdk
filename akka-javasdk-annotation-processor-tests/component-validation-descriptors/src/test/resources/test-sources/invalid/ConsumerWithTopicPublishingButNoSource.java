package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-publish-no-source")
@Produce.ToTopic("output-topic")
public class ConsumerWithTopicPublishingButNoSource extends Consumer {
  // Has topic publishing but no subscription source - should fail

  public Effect onMessage(String message) {
    return effects().done();
  }
}
