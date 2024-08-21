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

import java.util.List;

// With Multiple Subscriptions
@ComponentId("counters_by_value_ms")
public class CountersByValueSubscriptions extends View {

  public static class Counters extends TableUpdater<Counter> {

    @Override
    public Counter emptyRow() {
      return new Counter(0);
    }

    @Consume.FromEventSourcedEntity(CounterEntity.class)
    public Effect<Counter> onEvent(CounterEvent.ValueIncreased event) {
      return effects().updateRow(rowState().onValueIncreased(event));
    }

    @Consume.FromEventSourcedEntity(CounterEntity.class)
    public Effect<Counter> onEvent(CounterEvent.ValueMultiplied event) {
      return effects().updateRow(rowState().onValueMultiplied(event));
    }

    @Consume.FromEventSourcedEntity(CounterEntity.class)
    public Effect<Counter> onEvent(CounterEvent.ValueSet event) {
      return effects().updateRow(rowState().onValueSet(event));
    }

  }


  public record QueryParameters(int value) {}
  public record CounterList(List<Counter> counters) {}

  @Query("SELECT * AS counters FROM counters WHERE value = :value")
  public QueryEffect<CounterList> getCounterByValue(QueryParameters params) {
    return queryResult();
  }

}
