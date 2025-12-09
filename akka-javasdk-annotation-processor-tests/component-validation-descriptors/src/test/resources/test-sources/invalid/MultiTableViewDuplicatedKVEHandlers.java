/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Table;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "multi-table-view-duplicated-kve-handlers")
public class MultiTableViewDuplicatedKVEHandlers extends View {

  @Query("SELECT * FROM counters")
  public QueryEffect<CounterRow> getCounters() {
    return null;
  }

  public static class CounterRow {
    public int value;
  }

  @Table("counters")
  @Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
  public static class Counters extends TableUpdater<CounterRow> {
    public Effect<CounterRow> onEvent(Integer state) {
      return effects().updateRow(new CounterRow());
    }
  }

  @Table("counters2")
  @Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
  public static class Counters2 extends TableUpdater<CounterRow> {
    // Duplicate handler for the same type
    public Effect<CounterRow> onEvent(Integer state) {
      return effects().updateRow(new CounterRow());
    }

    // Another duplicate handler
    public Effect<CounterRow> onEvent2(Integer state) {
      return effects().updateRow(new CounterRow());
    }
  }
}
