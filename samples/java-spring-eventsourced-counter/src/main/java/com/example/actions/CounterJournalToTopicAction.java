package com.example.actions;

import com.example.Counter;
import com.example.CounterEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Publish;
import kalix.javasdk.annotations.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CounterJournalToTopicAction extends Action {

  private Logger logger = LoggerFactory.getLogger(CounterJournalToTopicAction.class);

  @Subscribe.EventSourcedEntity(value = Counter.class) // <1>
  @Publish.Topic("counter-events") // <2>
  public Action.Effect<CounterEvent> onValueIncreased(CounterEvent event) { // <3>
    logger.info("Received event: {}, publishing to topic counter-events", event.toString());
    return effects().reply(event); // <4>
  }
}
// end::class[]