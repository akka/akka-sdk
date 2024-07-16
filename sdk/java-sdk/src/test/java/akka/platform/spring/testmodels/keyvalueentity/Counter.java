/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.keyvalueentity;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import akka.platform.spring.testmodels.Number;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("ve-counter")
public class Counter extends KeyValueEntity<CounterState> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public CounterState emptyState() {
    return new CounterState(commandContext().entityId(), 0);
  }

  public Effect<Number> increase(Number num) {
    CounterState counterState = currentState();
    logger.info(
      "Increasing counter '{}' by '{}', current value is '{}'",
      counterState.id,
      num.value,
      counterState.value);
    CounterState newCounter = counterState.increase(num.value);
    return effects().updateState(newCounter).thenReply(new Number(newCounter.value));
  }

  public Effect<Number> randomIncrease(Integer value) {
    CounterState counterState = new CounterState(commandContext().entityId(), value);
    logger.info(
      "Increasing counter '{}' to value '{}'",
      counterState.id,
      counterState.value);
    return effects().updateState(counterState).thenReply(new Number(counterState.value));
  }

  public Effect<Number> get() {
    logger.info("Counter '{}' is '{}'", commandContext().entityId(), currentState().value);
    return effects().reply(new Number(currentState().value));
  }
}

