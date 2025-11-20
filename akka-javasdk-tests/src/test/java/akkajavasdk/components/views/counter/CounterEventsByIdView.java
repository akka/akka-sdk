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
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;

@Component(id = "counters_by_id")
public class CounterEventsByIdView extends View {

  public record CounterEntry(String id, CounterEvent latestEvent) {}

  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class Counters extends TableUpdater<CounterEntry> {

    public Effect<CounterEntry> onEvent(CounterEvent event) {
      return effects().updateRow(new CounterEntry(updateContext().eventSubject().get(), event));
    }

    @Override
    public CounterEntry emptyRow() {
      return new CounterEntry("", null); // Note: is never used
    }
  }

  @Query(value = "SELECT * FROM counters WHERE id = :counterId", streamUpdates = true)
  public QueryStreamEffect<CounterEntry> streamCounterUpdatesFor(String counterId) {
    return queryStreamResult();
  }
}
