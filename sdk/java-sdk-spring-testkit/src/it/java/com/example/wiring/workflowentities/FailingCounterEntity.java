/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import com.example.wiring.eventsourcedentities.counter.Counter;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TypeId("failing-counter")
public class FailingCounterEntity extends EventSourcedEntity<Counter, CounterEvent> {

  @Override
  public Counter emptyState() {
    return new Counter(0);
  }

  public Effect<Integer> increase(Integer value) {
    if (value % 3 != 0) {
      return effects().error("wrong value: " + value);
    } else {
      return effects()
          .emitEvent(new CounterEvent.ValueIncreased(value))
          .thenReply(Counter::value);
    }
  }

  public Effect<Integer> get() {
    return effects().reply(currentState().value());
  }

  @EventHandler
  public Counter handleIncrease(CounterEvent.ValueIncreased increased) {
    return currentState().onValueIncreased(increased);
  }

  @EventHandler
  public Counter handleMultiply(CounterEvent.ValueMultiplied multiplied) {
    return currentState().onValueMultiplied(multiplied);
  }

  @EventHandler
  public Counter handleSet(CounterEvent.ValueSet valueSet) {
    return currentState().onValueSet(valueSet);
  }
}
