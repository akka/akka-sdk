package com.example.actions;

import akka.Done;
import akka.platform.javasdk.consumer.Consumer;

// tag::class[]
public class SubscribeToRawBytesConsumer extends Consumer {

  public Effect onMessage(byte[] bytes) { // <1>
    // deserialization logic here
    return effects().done();
  }
}
// end::class[]