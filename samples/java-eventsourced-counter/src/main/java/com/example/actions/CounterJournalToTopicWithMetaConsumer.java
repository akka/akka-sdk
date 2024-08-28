package com.example.actions;

import akka.javasdk.Metadata;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;
import com.example.Counter;
import com.example.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::class[]
@ComponentId("counter-journal-to-topic-with-meta")
@Consume.FromEventSourcedEntity(value = Counter.class)
@Produce.ToTopic("counter-events-with-meta")
public class CounterJournalToTopicWithMetaConsumer extends Consumer {

  private Logger logger = LoggerFactory.getLogger(CounterJournalToTopicWithMetaConsumer.class);

  public Effect onValueIncreased(CounterEvent event) {
    String counterId = messageContext().metadata().get("ce-subject").orElseThrow(); // <1>
    Metadata metadata = Metadata.EMPTY.add("ce-subject", counterId);
    logger.info("Received event for counter id {}: {}", counterId, event);
    return effects().produce(event, metadata); // <2>
  }
}
// end::class[]