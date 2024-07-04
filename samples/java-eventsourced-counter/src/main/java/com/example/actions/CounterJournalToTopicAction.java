package com.example.actions;

import com.example.Counter;
import com.example.CounterEvent;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CounterJournalToTopicAction extends Action {

  private Logger logger = LoggerFactory.getLogger(CounterJournalToTopicAction.class);

  @Consume.FromEventSourcedEntity(value = Counter.class) // <1>
  @Produce.ToTopic("counter-events") // <2>
  public Action.Effect<CounterEvent> onValueIncreased(CounterEvent event) { // <3>
    logger.info("Received event: {}, publishing to topic counter-events", event.toString());
    return effects().reply(event); // <4>
  }
}
// end::class[]