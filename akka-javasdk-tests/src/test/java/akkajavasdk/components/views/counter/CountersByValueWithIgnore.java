/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.counter;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.eventsourcedentities.counter.Counter;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;

@ComponentId("counters_by_value_with_ignore")
public class CountersByValueWithIgnore extends View {

  @Consume.FromEventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true)
  public static class Counters extends TableUpdater<Counter> {
    @Override
    public Counter emptyRow() {
      return new Counter(0);
    }

    public Effect<Counter> onValueIncreased(CounterEvent.ValueIncreased event) {
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
