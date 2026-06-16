/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

// A public non-static (inner) updater must be accepted now that the isStatic guard is dropped.
@Component(id = "non-static-updater-view")
public class ViewWithNonStaticUpdater extends View {

  public static class Row {
    public String id;
    public String value;
  }

  @Query("SELECT * FROM rows")
  public QueryEffect<Row> getAll() {
    return queryResult();
  }

  @Consume.FromTopic("test-topic")
  public class Updater extends TableUpdater<Row> {
    public Effect<Row> onEvent(Row r) {
      return effects().updateRow(r);
    }
  }
}
