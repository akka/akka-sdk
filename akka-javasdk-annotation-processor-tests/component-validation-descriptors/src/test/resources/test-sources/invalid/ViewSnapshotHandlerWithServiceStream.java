package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-invalid-snapshot-stream")
public class ViewSnapshotHandlerWithServiceStream extends View {

  @Consume.FromServiceStream(service = "other-service", id = "events")
  public static class ViewUpdater extends TableUpdater<String> {

    @SnapshotHandler
    public Effect<String> onSnapshot(String snapshot) {
      return effects().updateRow(snapshot);
    }

    public Effect<String> onEvent(String event) {
      return effects().updateRow(event);
    }
  }

  @Query("SELECT * FROM view_invalid_snapshot_stream")
  public QueryEffect<String> getAll() {
    return queryResult();
  }
}
