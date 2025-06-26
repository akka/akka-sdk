/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.hierarchy;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akkajavasdk.components.eventsourcedentities.counter.Counter;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;

import java.util.Optional;

@ComponentId("counters_by_value_hierarchy")
public class HierarchyCountersByValue extends CountersByValueBaseClass {

  // needs to be in concrete class for now
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


  @Query("SELECT * FROM counters WHERE value = :value")
  public QueryEffect<Optional<Counter>> getCounterByValue(Integer value) {
    return queryResult();
  }
}
