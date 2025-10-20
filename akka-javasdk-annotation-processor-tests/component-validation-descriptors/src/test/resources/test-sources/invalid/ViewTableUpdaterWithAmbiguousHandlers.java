/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-table-updater-ambiguous")
public class ViewTableUpdaterWithAmbiguousHandlers extends View {

  @Query("SELECT * FROM counters")
  public QueryEffect<CounterRow> getCounters() {
    return null;
  }

  public static class CounterRow {
    public int value;
  }

  @Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
  public static class Counters extends TableUpdater<CounterRow> {
    // Ambiguous handlers - both handle the same type
    public Effect<CounterRow> onIncrement1(SimpleEventSourcedEntity.IncrementCounter event) {
      return effects().updateRow(new CounterRow());
    }

    public Effect<CounterRow> onIncrement2(SimpleEventSourcedEntity.IncrementCounter event) {
      return effects().updateRow(new CounterRow());
    }
  }
}
