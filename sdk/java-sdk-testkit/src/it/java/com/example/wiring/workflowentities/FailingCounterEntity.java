/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import com.example.wiring.eventsourcedentities.counter.Counter;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity;

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
          .persist(new CounterEvent.ValueIncreased(value))
          .thenReply(Counter::value);
    }
  }

  public Effect<Integer> get() {
    return effects().reply(currentState().value());
  }

  @Override
  public Counter applyEvent(CounterEvent event) {
    return switch (event) {
      case CounterEvent.ValueIncreased increased-> currentState().onValueIncreased(increased);
      case CounterEvent.ValueMultiplied multiplied -> currentState().onValueMultiplied(multiplied);
      case CounterEvent.ValueSet valueSet -> currentState().onValueSet(valueSet);
    };
  }
}
