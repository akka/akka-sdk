package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-valid-snapshot")
public class ValidViewWithSnapshotHandler extends View {

  public record ViewRow(int value) {}

  @Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
  public static class ViewUpdater extends TableUpdater<ViewRow> {

    @SnapshotHandler
    public Effect<ViewRow> onSnapshot(Integer snapshot) {
      return effects().updateRow(new ViewRow(snapshot));
    }

    public Effect<ViewRow> onEvent(SimpleEventSourcedEntity.CounterEvent event) {
      return switch (event) {
        case SimpleEventSourcedEntity.IncrementCounter inc -> effects().updateRow(new ViewRow(inc.value()));
        case SimpleEventSourcedEntity.DecrementCounter dec -> effects().updateRow(new ViewRow(dec.value()));
      };
    }
  }

  @Query("SELECT * FROM view_valid_snapshot")
  public QueryEffect<ViewRow> getAll() {
    return queryResult();
  }
}
