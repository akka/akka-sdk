package com.example.actions;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.consumer.Consumer;
import com.example.Counter;
import com.example.CounterEvent.ValueIncreased;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("subscribe-type-level")
@Consume.FromEventSourcedEntity(value = Counter.class, ignoreUnknown = true) // <1>
public class SubscribeTypeLevelConsumer extends Consumer {

  private Logger logger = LoggerFactory.getLogger(SubscribeTypeLevelConsumer.class);

  public Effect<Confirmed> onIncrease(ValueIncreased event) { // <2>
    logger.info("Received increased event: {} (msg ce id {})", event.toString(), messageContext().metadata().asCloudEvent().id());
    return effects().reply(Confirmed.instance); // <3>
  }
}
// end::class[]