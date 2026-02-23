package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-invalid-snapshot-kve")
public class ViewSnapshotHandlerWithKVE extends View {

  @Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
  public static class ViewUpdater extends TableUpdater<Integer> {

    @SnapshotHandler
    public Effect<Integer> onSnapshot(Integer snapshot) {
      return effects().updateRow(snapshot);
    }

  }

  @Query("SELECT * FROM view_invalid_snapshot_kve")
  public QueryEffect<Integer> getAll() {
    return queryResult();
  }
}
