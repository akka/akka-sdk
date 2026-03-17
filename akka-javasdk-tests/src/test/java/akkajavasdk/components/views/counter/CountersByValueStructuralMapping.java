/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
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
import java.util.List;

/**
 * Tests structural/duck-type mapping: the table stores Counter rows, but the query result maps them
 * to CounterDTO which is a different type with the same fields.
 */
@Component(id = "counters_by_value_structural_mapping")
public class CountersByValueStructuralMapping extends View {

  // Different type from Counter but structurally identical (same field names and types)
  public record CounterDTO(int value, String meta) {}

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class Counters extends TableUpdater<Counter> {

    @Override
    public Counter emptyRow() {
      return new Counter(0);
    }

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
  }

  public record QueryParameters(int value) {}

  public record CounterDTOList(List<CounterDTO> counters) {}

  @Query("SELECT * AS counters FROM counters WHERE value = :value")
  public QueryEffect<CounterDTOList> getCounterByValue(QueryParameters params) {
    return queryResult();
  }
}
