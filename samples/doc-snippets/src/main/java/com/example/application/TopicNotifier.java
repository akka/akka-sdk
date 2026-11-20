package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

// tag::start-from-topic[]
@Component(id = "topic-notifier")
@Consume.FromTopic(value = "shopping-cart-events", consumerGroup = "topic-notifier")
@Consume.StartFrom(latest = true)
public class TopicNotifier extends Consumer {

  public Effect onEvent(String event) {
    // send a notification
    return effects().done();
  }
}
// end::start-from-topic[]
