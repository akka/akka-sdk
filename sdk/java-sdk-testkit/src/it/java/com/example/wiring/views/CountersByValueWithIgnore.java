/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import akka.platform.javasdk.view.TableUpdater;
import com.example.wiring.eventsourcedentities.counter.Counter;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

@ComponentId("counters_by_value_with_ignore")
public class CountersByValueWithIgnore extends View {

  @Consume.FromEventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true)
  public static class Counters extends TableUpdater<Counter> {
    @Override
    public Counter emptyRow() {
      return new Counter(0);
    }

    public Effect<Counter> onValueIncreased(CounterEvent.ValueIncreased event){
      Counter counter = rowState();
      return effects().updateRow(counter.onValueIncreased(event));
    }
  }

  public record QueryParameters(Integer value) {}

  public static QueryParameters queryParam(Integer value) {
    return new QueryParameters(value);
  }

  @Query("SELECT * FROM counters WHERE value = :value")
  public QueryEffect<Counter> getCounterByValue(QueryParameters params) {
    return queryResult();
  }

}
