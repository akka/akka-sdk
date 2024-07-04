/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.action;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ActionId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.spring.testmodels.Done;
import akka.platform.spring.testmodels.valueentity.Counter;
import akka.platform.spring.testmodels.valueentity.CounterState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ActionId("counter-subscriber")
public class CounterSubscriber extends Action {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Consume.FromValueEntity(Counter.class)
  public Effect<Done> changes(CounterState counterState) {
    logger.info("Counter subscriber: counter id '{}' is '{}'", counterState.id, counterState.value);
    return effects().reply(Done.instance);
  }
}
