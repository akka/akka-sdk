package com.example.actions;

import com.example.Counter;
import com.example.CounterEvent;
import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::class[]
public class CounterJournalToTopicWithMetaAction extends Action {

  private Logger logger = LoggerFactory.getLogger(CounterJournalToTopicWithMetaAction.class);

  @Consume.FromEventSourcedEntity(value = Counter.class)
  @Produce.ToTopic("counter-events-with-meta")
  public Effect<CounterEvent> onValueIncreased(CounterEvent event) {
    String counterId = actionContext().metadata().get("ce-subject").orElseThrow(); // <1>
    Metadata metadata = Metadata.EMPTY.add("ce-subject", counterId);
    logger.info("Received event for counter id {}: {}", counterId, event);
    return effects().reply(event, metadata); // <2>
  }
}
// end::class[]