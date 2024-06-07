/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.eventsourcedentities.counter.Counter;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

@ViewId("counters_by_value_with_ignore")
@Table("counters_by_value_with_ignore")
@Subscribe.EventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true)
public class CountersByValueWithIgnore extends View<Counter> {

  public record QueryParameters(Integer value) {}

  public static QueryParameters queryParam(Integer value) {
    return new QueryParameters(value);
  }

  @Override
  public Counter emptyState() {
    return new Counter(0);
  }

  @Query("SELECT * FROM counters_by_value_with_ignore WHERE value = :value")
  public Counter getCounterByValue(QueryParameters params) {
    return null;
  }

  public UpdateEffect<Counter> onValueIncreased(CounterEvent.ValueIncreased event){
    Counter counter = viewState();
    return effects().updateState(counter.onValueIncreased(event));
  }
}
