package com.example.actions;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.consumer.Consumer;
import com.example.Counter;
import com.example.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter-journal-to-topic")
public class CounterJournalToTopicConsumer extends Consumer {

  private Logger logger = LoggerFactory.getLogger(CounterJournalToTopicConsumer.class);

  @Consume.FromEventSourcedEntity(value = Counter.class) // <1>
  @Produce.ToTopic("counter-events") // <2>
  public Effect<CounterEvent> onValueIncreased(CounterEvent event) { // <3>
    logger.info("Received event: {}, publishing to topic counter-events", event.toString());
    return effects().reply(event); // <4>
  }
}
// end::class[]