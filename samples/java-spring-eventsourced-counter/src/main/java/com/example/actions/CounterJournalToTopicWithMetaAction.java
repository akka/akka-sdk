package com.example.actions;

import com.example.Counter;
import com.example.CounterEvent;
import com.example.CounterEvent.ValueIncreased;
import com.example.CounterEvent.ValueMultiplied;
import kalix.javasdk.Metadata;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Publish;
import kalix.javasdk.annotations.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::class[]
@Subscribe.EventSourcedEntity(value = Counter.class)
public class CounterJournalToTopicWithMetaAction extends Action {

  private Logger logger = LoggerFactory.getLogger(CounterJournalToTopicWithMetaAction.class);

  @Publish.Topic("counter-events-with-meta")
  public Effect<CounterEvent> onValueIncreased(ValueIncreased event) {
    String counterId = actionContext().metadata().get("ce-subject").orElseThrow(); // <1>
    Metadata metadata = Metadata.EMPTY.add("ce-subject", counterId);
    logger.info("Received ValueIncreased event for counter id {}: {}", counterId, event);
    return effects().reply(event, metadata); // <2>
  }
  // end::class[]

  @Publish.Topic("counter-events-with-meta")
  public Effect<CounterEvent> onValueMultiplied(ValueMultiplied event) {
    String counterId = actionContext().metadata().get("ce-subject").orElseThrow();
    Metadata metadata = Metadata.EMPTY.add("ce-subject", counterId);
    logger.info("Received ValueMultiplied event for counter id {}: {}", counterId, event);
    return effects().reply(event, metadata);
  }
// tag::class[]
}
// end::class[]