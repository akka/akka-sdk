/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import akka.platform.javasdk.view.TableUpdater;
import com.example.wiring.eventsourcedentities.counter.*;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;

@ComponentId("counters_by_value")
public class CountersByValue extends View {

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class Counters extends TableUpdater<Counter> {

    public Effect<Counter> onEvent(CounterEvent event) {
      Counter counter = rowState();
      var updatedCounter = switch(event) {
        case CounterEvent.ValueIncreased valueIncreased -> counter.onValueIncreased(valueIncreased);
        case CounterEvent.ValueMultiplied valueMultiplied -> counter.onValueMultiplied(valueMultiplied);
        case CounterEvent.ValueSet valueSet -> counter.onValueSet(valueSet);
      };
      return effects().updateRow(updatedCounter);
    }

    @Override
    public Counter emptyRow() {
      return new Counter(0);
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
