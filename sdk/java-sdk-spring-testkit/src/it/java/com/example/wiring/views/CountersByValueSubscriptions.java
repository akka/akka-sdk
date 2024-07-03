/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.eventsourcedentities.counter.Counter;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Consume;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

import java.util.List;

// With Multiple Subscriptions
@ViewId("counters_by_value_ms")
@Table("counters_by_value_ms")
public class CountersByValueSubscriptions extends View<Counter> {


  public record QueryParameters(int value) {}
  public record CounterList(List<Counter> counters) {}

  @Override
  public Counter emptyState() {
    return new Counter(0);
  }

  @Query("SELECT * AS counters FROM counters_by_value_ms WHERE value = :value")
  public CounterList getCounterByValue(QueryParameters params) {
    return null;
  }

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public Effect<Counter> onEvent(CounterEvent.ValueIncreased event) {
    return effects().updateState(viewState().onValueIncreased(event));
  }

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public Effect<Counter> onEvent(CounterEvent.ValueMultiplied event) {
    return effects().updateState(viewState().onValueMultiplied(event));
  }

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public Effect<Counter> onEvent(CounterEvent.ValueSet event) {
    return effects().updateState(viewState().onValueSet(event));
  }
}
