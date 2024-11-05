/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.counter;

import akka.javasdk.view.TableUpdater;
import akkajavasdk.components.eventsourcedentities.counter.Counter;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;

import java.util.List;

// With Multiple Subscriptions
@ComponentId("counters_by_value_ms")
public class CountersByValueSubscriptions extends View {

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class Counters extends TableUpdater<Counter> {

    @Override
    public Counter emptyRow() {
      return new Counter(0);
    }

    public Effect<Counter> onEvent(CounterEvent.ValueIncreased event) {
      return effects().updateRow(rowState().onValueIncreased(event));
    }

    public Effect<Counter> onEvent(CounterEvent.ValueMultiplied event) {
      return effects().updateRow(rowState().onValueMultiplied(event));
    }

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
