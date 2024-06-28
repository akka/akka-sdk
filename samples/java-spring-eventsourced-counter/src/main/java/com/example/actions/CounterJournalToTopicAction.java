package com.example.actions;

import com.example.Counter;
import com.example.CounterEvent;
import com.example.CounterEvent.ValueIncreased;
import com.example.CounterEvent.ValueMultiplied;

// tag::class[]
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Publish;
import kalix.javasdk.annotations.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Subscribe.EventSourcedEntity(value = Counter.class) // <1>
public class CounterJournalToTopicAction extends Action {

  private Logger logger = LoggerFactory.getLogger(CounterJournalToTopicAction.class);

  @Publish.Topic("counter-events") // <2>
  public Action.Effect<CounterEvent> onValueIncreased(ValueIncreased event) { // <3>
    logger.info("Received increase event: {} publishing to topic counter-events", event.toString());
    return effects().reply(event); // <4>
  }
  // end::class[]

  @Publish.Topic("counter-events")
  public Action.Effect<CounterEvent> onValueMultiplied(ValueMultiplied event) {
    logger.info("Received multiplied event: {} publishing to topic counter-events", event.toString());
    return effects().reply(event);
  }
// tag::class[]
}
// end::class[]