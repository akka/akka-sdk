package com.example.actions;

import akka.Done;
import akka.platform.javasdk.consumer.Consumer;

// tag::class[]
public class SubscribeToRawBytesConsumer extends Consumer {

  public Effect<Done> onMessage(byte[] bytes) { // <1>
    // deserialization logic here
    return effects().reply(Done.done());
  }
}
// end::class[]