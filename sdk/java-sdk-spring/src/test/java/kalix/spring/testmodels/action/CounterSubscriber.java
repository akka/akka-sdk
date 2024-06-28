/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.testmodels.action;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.ActionId;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.testmodels.Done;
import kalix.spring.testmodels.valueentity.Counter;
import kalix.spring.testmodels.valueentity.CounterState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ActionId("counter-subscriber")
public class CounterSubscriber extends Action {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Subscribe.ValueEntity(Counter.class)
  public Effect<Done> changes(CounterState counterState) {
    logger.info("Counter subscriber: counter id '{}' is '{}'", counterState.id, counterState.value);
    return effects().reply(Done.instance);
  }
}
