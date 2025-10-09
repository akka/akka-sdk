/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.counter;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.eventsourcedentities.counter.Counter;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;
import java.util.Optional;

@Component(id = "counters_by_value")
public class CountersByValue extends View {

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class Counters extends TableUpdater<Counter> {

    public Effect<Counter> onEvent(CounterEvent event) {
      Counter counter = rowState();
      var updatedCounter =
          switch (event) {
            case CounterEvent.ValueIncreased valueIncreased ->
                counter.onValueIncreased(valueIncreased);
            case CounterEvent.ValueMultiplied valueMultiplied ->
                counter.onValueMultiplied(valueMultiplied);
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
  public QueryEffect<Optional<Counter>> getCounterByValue(QueryParameters params) {
    return queryResult();
  }
}
