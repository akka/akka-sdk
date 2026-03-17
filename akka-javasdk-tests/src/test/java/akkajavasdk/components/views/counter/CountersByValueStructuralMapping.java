/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.counter;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;
import java.util.List;

/**
 * Tests structural/duck-type mapping: the table stores CounterRow rows, but the query result maps
 * them to CounterDTO — a different type with the same field names and types.
 */
@Component(id = "counters_by_value_structural_mapping")
public class CountersByValueStructuralMapping extends View {

  // Custom table row type with three fields
  public record CounterRow(String entityId, int value, String meta) {}

  // Different type from CounterRow but structurally identical (same field names and types)
  public record CounterDTO(String entityId, int value, String meta) {}

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class Counters extends TableUpdater<CounterRow> {

    @Override
    public CounterRow emptyRow() {
      return new CounterRow(updateContext().eventSubject().orElse(""), 0, "");
    }

    public Effect<CounterRow> onEvent(CounterEvent event) {
      CounterRow row = rowState();
      var updated =
          switch (event) {
            case CounterEvent.ValueIncreased e ->
                new CounterRow(row.entityId(), row.value() + e.value(), row.meta());
            case CounterEvent.ValueMultiplied e ->
                new CounterRow(row.entityId(), row.value() * e.value(), row.meta());
            case CounterEvent.ValueSet e -> new CounterRow(row.entityId(), e.value(), row.meta());
          };
      return effects().updateRow(updated);
    }
  }

  public record QueryParameters(int value) {}

  public record CounterDTOList(List<CounterDTO> counters) {}

  @Query("SELECT * AS counters FROM counters WHERE value = :value")
  public QueryEffect<CounterDTOList> getCounterByValue(QueryParameters params) {
    return queryResult();
  }
}
