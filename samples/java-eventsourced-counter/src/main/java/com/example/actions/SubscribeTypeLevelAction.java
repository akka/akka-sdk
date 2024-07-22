package com.example.actions;

import akka.platform.javasdk.annotations.ComponentId;
import com.example.Counter;
import com.example.CounterEvent.ValueIncreased;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("subscribe-type-level")
@Consume.FromEventSourcedEntity(value = Counter.class, ignoreUnknown = true) // <1>
public class SubscribeTypeLevelAction extends Action {

  private Logger logger = LoggerFactory.getLogger(SubscribeTypeLevelAction.class);

  public Action.Effect<Confirmed> onIncrease(ValueIncreased event) { // <2>
    logger.info("Received increased event: {} (msg ce id {})", event.toString(), actionContext().metadata().asCloudEvent().id());
    return effects().reply(Confirmed.instance); // <3>
  }
}
// end::class[]