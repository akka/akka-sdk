/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.eventsourcedentities.counter.Counter;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ViewId;
import akka.platform.javasdk.view.View;

@ViewId("counters_by_value_with_ignore")
@Table("counters_by_value_with_ignore")
@Consume.FromEventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true)
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

  public Effect<Counter> onValueIncreased(CounterEvent.ValueIncreased event){
    Counter counter = viewState();
    return effects().updateState(counter.onValueIncreased(event));
  }
}
