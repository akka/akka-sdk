package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.annotations.StartFrom;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-start-from-producer")
@Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
@Produce.ServiceStream(id = "my-stream")
@StartFrom(latest = true)
public class ConsumerStartFromServiceStreamProducer extends Consumer {

  public Effect onEvent(SimpleEventSourcedEntity.CounterEvent event) {
    return effects().produce(event);
  }

}
