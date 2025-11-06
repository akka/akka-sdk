/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-missing-event-handler-type-level")
public class ViewMissingEventHandlerTypeLevel extends View {

  @Query("SELECT * FROM counters")
  public QueryEffect<CounterRow> getCounters() {
    return null;
  }

  public static class CounterRow {
    public int value;
  }

  @Consume.FromEventSourcedEntity(value = SimpleEventSourcedEntity.class, ignoreUnknown = false)
  public static class Counters extends TableUpdater<CounterRow> {
    // Only handles IncrementCounter, missing handler for DecrementCounter
    // With ignoreUnknown = false, this should fail
    public Effect<CounterRow> onIncrement(SimpleEventSourcedEntity.IncrementCounter event) {
      return effects().updateRow(new CounterRow());
    }
  }
}
