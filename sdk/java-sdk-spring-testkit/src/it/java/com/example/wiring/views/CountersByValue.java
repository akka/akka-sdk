/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.eventsourcedentities.counter.*;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;

@ViewId("counters_by_value")
@Table("counters_by_value")
public class CountersByValue extends View<Counter> {

  @Override
  public Counter emptyState() {
    return new Counter(0);
  }

  public record QueryParameters(Integer value) {}

  public static QueryParameters queryParam(Integer value) {
    return new QueryParameters(value);
  }

  @Query("SELECT * FROM counters_by_value WHERE value = :value")
  public Counter getCounterByValue(QueryParameters params) {
    return null;
  }

  @Subscribe.EventSourcedEntity(CounterEntity.class)
  public UpdateEffect<Counter> onEvent(CounterEvent.ValueIncreased event) {
    Counter counter = viewState();
    return effects().updateState(counter.onValueIncreased(event));
  }

  @Subscribe.EventSourcedEntity(CounterEntity.class)
  public UpdateEffect<Counter> onEvent(CounterEvent.ValueMultiplied event) {
    Counter counter = viewState();
    return effects().updateState(counter.onValueMultiplied(event));
  }

  @Subscribe.EventSourcedEntity(CounterEntity.class)
  public UpdateEffect<Counter> onEvent(CounterEvent.ValueSet event) {
    Counter counter = viewState();
    return effects().updateState(counter.onValueSet(event));
  }
}
